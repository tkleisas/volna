#ifndef VAEngine_h_
#define VAEngine_h_

#include <oboe/Oboe.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstring>
#include <mutex>

#include "Delay.h"
#include "EngineEventQueue.h"
#include "SynthVoice.h"
#include "effects/Limiter.h"

template <int numvoices, int WAVEFORM_COUNT, int WTLEN>
class VAEngine : public oboe::AudioStreamCallback {
public:
    VAEngine(float* newWaveforms, const float* factoryWaveforms)
        : waveforms(newWaveforms), factoryWaveforms(factoryWaveforms) {}

    ~VAEngine() override {
        shutdown();
    }

    void init(float newSampleRate) {
        this->sampleRate = static_cast<int>(newSampleRate);
        if (this->sampleRate <= 0) {
            this->sampleRate = 48000;
        }
        mSafetyLimiterEffect.init(sampleRate, 2048);
        mSafetyLimiterEffect.setParameter(
            yawn::effects::Limiter::kCeiling, -1.0f);
        mSafetyLimiterEffect.setParameter(
            yawn::effects::Limiter::kRelease, 80.0f);
        mSafetyLimiterEffect.setParameter(
            yawn::effects::Limiter::kLookahead, 3.0f);
        mSafetyLimiterEffect.reset();
        mSafetyLimiter.store(true);
        mPolyphonyLeveling.store(true);
        mPolyphonyGain = 1.0f;

        delay = new Delay(this->sampleRate);
        delay->SetFeedback(0.5f);
        delay->SetDelay(this->sampleRate);
        delay->Reset();
        delayLevel = 0.0f;

        recBufferLen = this->sampleRate * 60;
        recBuffer = new float[recBufferLen]();
        playBufferLen = recBufferLen;
        playBuffer = new float[playBufferLen]();
        resetTransportState();

        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].SetSampleRate(static_cast<float>(this->sampleRate));
            for (int j = 0; j < WAVEFORM_COUNT; j++) {
                mSynthVoice[i].AddOsc1SharedWaveTable(WTLEN, &waveforms[j * WTLEN]);
                mSynthVoice[i].AddOsc2SharedWaveTable(WTLEN, &waveforms[j * WTLEN]);
            }
            voices_notes[i] = -1;
        }

        SetupRampUp(0.0f, 1.0f, 20);
        SetupRampDown(0.0f, 1.0f, 40);
        SetStartTrim(100);
        SetEndTrim(250);
        crossfadeSamples = static_cast<int>(sampleRate / 50); // 20 ms
        playFadeSamples = static_cast<int>(sampleRate / 100); // 10 ms
        playFadeRemaining = 0;
        audioSuspended.store(false);
        dspLoad.store(0.0f);
        dspPeak.store(0.0f);
        oboeInit();
    }

    void shutdown() {
        audioSuspended.store(false);
        if (outStream) {
            outStream.reset();
        }
        delete delay;
        delay = nullptr;
        delete[] recBuffer;
        recBuffer = nullptr;
        delete[] playBuffer;
        playBuffer = nullptr;
        recBufferLen = 0;
        playBufferLen = 0;
        recording = false;
        playing = false;
    }

    void pauseAudio() {
        audioSuspended.store(true);
        if (outStream) {
            outStream->stop();
        }
        for (int i = 0; i < numvoices; i++) {
            voices_notes[i] = -1;
            mSynthVoice[i].Reset();
        }
        recording = false;
        playing = false;
        playFadeRemaining = 0;
        loopRecordingAtomic.store(false);
        loopPlayingAtomic.store(false);
        loopPlayIndexAtomic.store(0);
    }

    void resumeAudio() {
        audioSuspended.store(false);
        if (!outStream) {
            oboeInit();
            return;
        }
        const oboe::StreamState state = outStream->getState();
        switch (state) {
            case oboe::StreamState::Closed:
            case oboe::StreamState::Closing:
            case oboe::StreamState::Disconnected:
            case oboe::StreamState::Uninitialized:
            case oboe::StreamState::Unknown:
            case oboe::StreamState::Pausing:
            case oboe::StreamState::Stopping:
                oboeInit();
                break;
            case oboe::StreamState::Stopped:
            case oboe::StreamState::Paused:
            case oboe::StreamState::Open:
                outStream->requestStart();
                break;
            default:
                break;
        }
    }

    // Replaces every slot of the shared wavetable bank. The stream is
    // stopped while the copy happens so the audio thread never observes
    // a half-written table.
    bool setWavetableBank(const float* bank) {
        if (bank == nullptr || waveforms == nullptr) {
            return false;
        }
        std::lock_guard<std::mutex> lock(bankMutex);
        const bool wasRunning = audioIsRunning();
        if (outStream) {
            outStream->stop();
        }
        std::memcpy(waveforms, bank,
                    sizeof(float) * WAVEFORM_COUNT * WTLEN);
        if (wasRunning && outStream) {
            outStream->requestStart();
        }
        return true;
    }

    bool resetWavetableBank() {
        return factoryWaveforms != nullptr && setWavetableBank(factoryWaveforms);
    }

    void oboeInit() {
        if (outStream) {
            outStream.reset();
        }
        oboe::AudioStreamBuilder builder;
        builder.setSharingMode(oboe::SharingMode::Exclusive)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setDirection(oboe::Direction::Output)
            ->setChannelCount(kChannelCount)
            ->setSampleRate(sampleRate)
            ->setFormat(oboe::AudioFormat::Float)
            ->setCallback(this);

        const oboe::Result result = builder.openManagedStream(outStream);
        if (result != oboe::Result::OK) {
            outStream.reset();
            return;
        }
        outStream->requestStart();
    }

    void onErrorBeforeClose(oboe::AudioStream* /* oboeStream */,
                            oboe::Result /* error */) override {}

    void onErrorAfterClose(oboe::AudioStream* /* oboeStream */,
                           oboe::Result /* error */) override {
        if (!audioSuspended.load()) {
            oboeInit();
        }
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* /* oboeStream */,
                                          void* audioData,
                                          int32_t numFrames) override {
        if (numFrames <= 0) {
            return oboe::DataCallbackResult::Continue;
        }
        const auto callbackStart = std::chrono::steady_clock::now();
        float* floatData = static_cast<float*>(audioData);
        processEvents();

        int activeVoices = 0;
        for (int v = 0; v < numvoices; ++v) {
            if (voices_notes[v] >= 0) {
                activeVoices++;
            }
        }
        const int effectiveVoices = std::max(1, activeVoices);
        const float polyTarget = mPolyphonyLeveling.load()
            ? 1.0f / std::sqrt(static_cast<float>(effectiveVoices))
            : 1.0f;
        const float polySmooth = 1.0f - std::exp(
            -1.0f / (0.02f * static_cast<float>(sampleRate)));

        for (int i = 0; i < numFrames; ++i) {
            mPolyphonyGain += polySmooth * (polyTarget - mPolyphonyGain);
            float sampleValue = Process();
            if (playing) {
                sampleValue += nextPlaybackSample();
            }
            sampleValue *= mPolyphonyGain;

            wavedata[index] = sampleValue;
            index = (index + 1) % kWaveformSize;

            for (int j = 0; j < kChannelCount; j++) {
                floatData[i * kChannelCount + j] = sampleValue;
            }

            if (recording) {
                recordSample(sampleValue);
            }
        }
        loopRecordingAtomic.store(recording);
        loopPlayingAtomic.store(playing);
        loopRecordLengthAtomic.store(recIndexEnd);
        loopPlayIndexAtomic.store(playIndex);
        loopPlayEndAtomic.store(playIndexEnd);
        if (mSafetyLimiter.load()) {
            mSafetyLimiterEffect.process(floatData, numFrames, kChannelCount);
        }
        const double elapsed = std::chrono::duration<double>(
            std::chrono::steady_clock::now() - callbackStart).count();
        const double budget = static_cast<double>(numFrames) /
                              static_cast<double>(sampleRate);
        if (budget > 0.0) {
            const float instant = static_cast<float>(elapsed / budget);
            const float previous = dspLoad.load(std::memory_order_relaxed);
            dspLoad.store(previous * 0.8f + instant * 0.2f,
                          std::memory_order_relaxed);
            if (instant > dspPeak.load(std::memory_order_relaxed)) {
                dspPeak.store(instant, std::memory_order_relaxed);
            }
        }
        return oboe::DataCallbackResult::Continue;
    }

    float Process() {
        float s = 0.0f;
        for (int i = 0; i < numvoices; i++) {
            s += mSynthVoice[i].Process();
        }
        s += delayLevel * delay->Process(s);
        return s / static_cast<float>(numvoices);
    }

    void handleNoteSpread(int channel, int note, int spread) {
        EngineEvent event;
        event.type = EngineEvent::Type::NoteSpread;
        event.channel = static_cast<uint8_t>(channel);
        event.a = static_cast<uint8_t>(note);
        event.b = static_cast<uint8_t>(spread);
        pushEvent(event);
    }

    void handleSelectWaveform(uint8_t channel, uint8_t osc, uint8_t note,
                              uint8_t wave) {
        EngineEvent event;
        event.type = EngineEvent::Type::SelectWaveform;
        event.channel = channel;
        event.a = osc;
        event.b = note;
        event.c = wave;
        pushEvent(event);
    }

    void handleNoteOn(int channel, int note, int velocity) {
        EngineEvent event;
        event.type = EngineEvent::Type::NoteOn;
        event.channel = static_cast<uint8_t>(channel);
        event.a = static_cast<uint8_t>(note);
        event.b = static_cast<uint8_t>(velocity);
        pushEvent(event);
    }

    void handleNoteTransition(int channel, int oldNote, int newNote,
                              int velocity) {
        EngineEvent event;
        event.type = EngineEvent::Type::NoteTransition;
        event.channel = static_cast<uint8_t>(channel);
        event.a = static_cast<uint8_t>(oldNote);
        event.b = static_cast<uint8_t>(newNote);
        event.c = static_cast<uint8_t>(velocity);
        pushEvent(event);
    }

    void handleNotePitch(int channel, int note, float bendSemitones) {
        EngineEvent event;
        event.type = EngineEvent::Type::NotePitch;
        event.channel = static_cast<uint8_t>(channel);
        event.a = static_cast<uint8_t>(note);
        event.f = bendSemitones;
        pushEvent(event);
    }

    void handleNoteOff(int channel, int note, int velocity) {
        EngineEvent event;
        event.type = EngineEvent::Type::NoteOff;
        event.channel = static_cast<uint8_t>(channel);
        event.a = static_cast<uint8_t>(note);
        event.b = static_cast<uint8_t>(velocity);
        pushEvent(event);
    }

    void handlePitchBend(int channel, uint8_t bendLsb, uint8_t bendMsb) {
        EngineEvent event;
        event.type = EngineEvent::Type::PitchBend;
        event.channel = static_cast<uint8_t>(channel);
        event.a = bendLsb;
        event.b = bendMsb;
        pushEvent(event);
    }

    void handlePitchBend(uint8_t /* channel */, int bend) {
        handlePitchBend(0, static_cast<uint8_t>(bend & 0x7F),
                        static_cast<uint8_t>((bend >> 7) & 0x7F));
    }

    void handleSetTet(int tet) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetTet;
        event.i = tet;
        pushEvent(event);
    }

    void handleSetTune(float tune) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetTune;
        event.f = tune;
        pushEvent(event);
    }

    void handleSetOctaveFactor(float factor) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetOctaveFactor;
        event.f = factor;
        pushEvent(event);
    }

    void setADSR(uint8_t a, uint8_t d, uint8_t s, uint8_t r) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetAdsr;
        event.a = a;
        event.b = d;
        event.c = s;
        event.d = r;
        pushEvent(event);
    }

    void setOsc1Wave(uint8_t wave) {
        osc1Wave.store(wave);
        EngineEvent event;
        event.type = EngineEvent::Type::SetOsc1Wave;
        event.a = wave;
        pushEvent(event);
    }

    uint8_t getOsc1Wave() {
        return osc1Wave.load();
    }

    void setOsc2Wave(uint8_t wave) {
        osc2Wave.store(wave);
        EngineEvent event;
        event.type = EngineEvent::Type::SetOsc2Wave;
        event.a = wave;
        pushEvent(event);
    }

    uint8_t getOsc2Wave() {
        return osc2Wave.load();
    }

    void setPwm(uint8_t pwm) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetPwm;
        event.a = pwm;
        pushEvent(event);
    }

    void handleControlChange(uint8_t channel, uint8_t control, uint8_t value) {
        EngineEvent event;
        event.type = EngineEvent::Type::ControlChange;
        event.channel = channel;
        event.a = control;
        event.b = value;
        pushEvent(event);
    }

    float* getWavedata() {
        return wavedata;
    }

    static constexpr int kWaveformSize = 1024;

    void copyWaveform(float* out, int length) const {
        if (out == nullptr || length <= 0) {
            return;
        }
        const int count = std::min(length, kWaveformSize);
        const int start =
            (index - count + kWaveformSize) % kWaveformSize;
        for (int i = 0; i < count; ++i) {
            out[i] = wavedata[(start + i) % kWaveformSize];
        }
    }

    float getDspLoad() const { return dspLoad.load(std::memory_order_relaxed); }
    float getDspPeak() const { return dspPeak.load(std::memory_order_relaxed); }
    void resetDspPeak() { dspPeak.store(0.0f, std::memory_order_relaxed); }

    void SetPlay(bool flag) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetPlay;
        event.a = flag ? 1 : 0;
        pushEvent(event);
    }

    void SetRecord(bool flag) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetRecord;
        event.a = flag ? 1 : 0;
        pushEvent(event);
    }

    void setSafetyLimiter(bool enabled) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetSafetyLimiter;
        event.a = enabled ? 1 : 0;
        pushEvent(event);
    }

    void setPolyphonyLeveling(bool enabled) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetPolyphonyLeveling;
        event.a = enabled ? 1 : 0;
        pushEvent(event);
    }

    void clearLoop() {
        EngineEvent event;
        event.type = EngineEvent::Type::ClearLoop;
        pushEvent(event);
    }

    float loopRecordProgress() const {
        if (recBufferLen <= 0) {
            return 0.0f;
        }
        return static_cast<float>(
                   loopRecordLengthAtomic.load()) /
               static_cast<float>(recBufferLen);
    }

    float loopPlayProgress() const {
        const int end = loopPlayEndAtomic.load();
        if (end <= 0) {
            return 0.0f;
        }
        const float progress = static_cast<float>(
                                   loopPlayIndexAtomic.load()) /
                               static_cast<float>(end);
        return std::min(1.0f, std::max(0.0f, progress));
    }

    bool isLoopRecording() const { return loopRecordingAtomic.load(); }
    bool isLoopPlaying() const { return loopPlayingAtomic.load(); }
    bool hasLoop() const { return loopRecordLengthAtomic.load() > 0; }

    void SetupRampUp(float upMinValue, float upMaxValue, int ms) {
        upSamples = static_cast<int>((static_cast<float>(ms) * sampleRate) / 1000.0f);
        if (upSamples > 0) {
            upFactor = (upMaxValue - upMinValue) / upSamples;
        } else {
            upFactor = 0.0f;
        }
        upMin = upMinValue;
        upTimeMs = ms;
    }

    void SetupRampDown(float downMinValue, float downMaxValue, int ms) {
        downSamples = static_cast<int>((static_cast<float>(ms) * sampleRate) /
                                       1000.0f);
        if (downSamples > 0) {
            downFactor = (downMaxValue - downMinValue) / downSamples;
        } else {
            downFactor = 0.0f;
        }
        downMin = downMinValue;
        downTimeMs = ms;
    }

    int MillisecondsToSamples(int millis) {
        return static_cast<int>((static_cast<double>(millis) * sampleRate) /
                                1000.0);
    }

    void SetStartTrim(int ms) {
        trimStart = MillisecondsToSamples(ms);
    }

    void SetEndTrim(int ms) {
        trimEnd = MillisecondsToSamples(ms);
    }

    void SetDelayLevel(float level) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetDelayLevel;
        event.f = level;
        pushEvent(event);
    }

    void SetDelayLength(int length) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetDelayLength;
        event.i = length;
        pushEvent(event);
    }

    void SetDelayFeedback(float feedback) {
        EngineEvent event;
        event.type = EngineEvent::Type::SetDelayFeedback;
        event.f = feedback;
        pushEvent(event);
    }

private:
    bool pushEvent(const EngineEvent& event) {
        return eventQueue.push(event);
    }

    bool audioIsRunning() const {
        if (!outStream) {
            return false;
        }
        const oboe::StreamState state = outStream->getState();
        return state == oboe::StreamState::Starting ||
               state == oboe::StreamState::Started;
    }

    void processEvents() {
        EngineEvent event;
        while (eventQueue.pop(event)) {
            dispatchEvent(event);
        }
    }

    void dispatchEvent(const EngineEvent& event) {
        switch (event.type) {
            case EngineEvent::Type::NoteOn:
                doNoteOn(event.channel, event.a, event.b);
                break;
            case EngineEvent::Type::NoteOff:
                doNoteOff(event.channel, event.a, event.b);
                break;
            case EngineEvent::Type::NotePitch:
                doNotePitch(event.channel, event.a, event.f);
                break;
            case EngineEvent::Type::NoteTransition:
                doNoteTransition(event.channel, event.a, event.b, event.c);
                break;
            case EngineEvent::Type::NoteSpread:
                doNoteSpread(event.channel, event.a, event.b);
                break;
            case EngineEvent::Type::PitchBend:
                doPitchBend(event.channel, event.a, event.b);
                break;
            case EngineEvent::Type::ControlChange:
                doControlChange(event.channel, event.a, event.b);
                break;
            case EngineEvent::Type::SelectWaveform:
                doSelectWaveform(event.channel, event.a, event.b, event.c);
                break;
            case EngineEvent::Type::SetTet:
                doSetTet(event.i);
                break;
            case EngineEvent::Type::SetTune:
                doSetTune(event.f);
                break;
            case EngineEvent::Type::SetOctaveFactor:
                doSetOctaveFactor(event.f);
                break;
            case EngineEvent::Type::SetOsc1Wave:
                doSetOsc1Wave(event.a);
                break;
            case EngineEvent::Type::SetOsc2Wave:
                doSetOsc2Wave(event.a);
                break;
            case EngineEvent::Type::SetAdsr:
                doSetAdsr(event.a, event.b, event.c, event.d);
                break;
            case EngineEvent::Type::SetPwm:
                doSetPwm(event.a);
                break;
            case EngineEvent::Type::SetDelayLevel:
                delayLevel = event.f;
                break;
            case EngineEvent::Type::SetDelayLength:
                if (delay) {
                    delay->SetDelay(event.i);
                }
                break;
            case EngineEvent::Type::SetDelayFeedback:
                if (delay) {
                    delay->SetFeedback(event.f);
                }
                break;
            case EngineEvent::Type::SetPlay:
                doSetPlay(event.a != 0);
                break;
            case EngineEvent::Type::SetRecord:
                doSetRecord(event.a != 0);
                break;
            case EngineEvent::Type::ClearLoop:
                resetTransportState();
                break;
            case EngineEvent::Type::SetSafetyLimiter:
                mSafetyLimiter.store(event.a != 0);
                if (event.a != 0) {
                    mSafetyLimiterEffect.reset();
                }
                break;
            case EngineEvent::Type::SetPolyphonyLeveling:
                mPolyphonyLeveling.store(event.a != 0);
                if (event.a == 0) {
                    mPolyphonyGain = 1.0f;
                }
                break;
            default:
                break;
        }
    }

    void doNoteSpread(uint8_t /* channel */, uint8_t note, uint8_t spread) {
        for (int i = 0; i < numvoices; i++) {
            if (voices_notes[i] == note) {
                mSynthVoice[i].noteSpread(note, spread);
            }
        }
    }

    void doSelectWaveform(uint8_t /* channel */, uint8_t osc, uint8_t note,
                          uint8_t wave) {
        for (int i = 0; i < numvoices; i++) {
            if (voices_notes[i] == note) {
                if (osc == 0) {
                    mSynthVoice[i].MidiOsc1Wave(wave % 256);
                } else {
                    mSynthVoice[i].MidiOsc2Wave(wave % 256);
                }
            }
        }
    }

    void doNoteOn(uint8_t /* channel */, uint8_t note, uint8_t velocity) {
        int maxNote = -1;
        int maxNoteIndex = -1;
        for (int i = 0; i < numvoices; i++) {
            if (voices_notes[i] == -1) {
                voices_notes[i] = note;
                mSynthVoice[i].MidiNoteOn(note, velocity);
                return;
            }
            if (voices_notes[i] > maxNote) {
                maxNote = voices_notes[i];
                maxNoteIndex = i;
            }
        }
        if (maxNoteIndex >= 0) {
            voices_notes[maxNoteIndex] = note;
            mSynthVoice[maxNoteIndex].MidiNoteOn(note, velocity);
        }
    }

    void doNoteTransition(uint8_t /* channel */, uint8_t oldNote,
                          uint8_t newNote, uint8_t velocity) {
        for (int i = 0; i < numvoices; i++) {
            if (voices_notes[i] == oldNote) {
                voices_notes[i] = newNote;
                mSynthVoice[i].MidiChangeNote(newNote, velocity);
            }
        }
    }

    void doNoteOff(uint8_t /* channel */, uint8_t note,
                   uint8_t /* velocity */) {
        for (int i = 0; i < numvoices; i++) {
            if (voices_notes[i] == note) {
                voices_notes[i] = -1;
                mSynthVoice[i].MidiNoteOff();
            }
        }
    }

    void doNotePitch(uint8_t /* channel */, uint8_t note, float bendSemitones) {
        for (int i = 0; i < numvoices; i++) {
            if (voices_notes[i] == note) {
                mSynthVoice[i].SetNoteBend(bendSemitones);
            }
        }
    }

    void doPitchBend(uint8_t /* channel */, uint8_t bendLsb,
                     uint8_t bendMsb) {
        const uint16_t bend =
            static_cast<uint16_t>((bendMsb << 7) | bendLsb);
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].MidiBend(bend);
        }
    }

    void doControlChange(uint8_t channel, uint8_t control, uint8_t value) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].ControlChange(channel, control, value);
        }
    }

    void doSetTet(int tet) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].SetTet(static_cast<float>(tet));
        }
    }

    void doSetTune(float tune) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].SetTune(tune);
        }
    }

    void doSetOctaveFactor(float factor) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].SetOctaveFactor(factor);
        }
    }

    void doSetAdsr(uint8_t a, uint8_t d, uint8_t s, uint8_t r) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].SetOsc1ADSR(a, d, s, r);
            mSynthVoice[i].SetOsc2ADSR(a, d, s, r);
        }
    }

    void doSetOsc1Wave(uint8_t wave) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].MidiOsc1Wave(wave);
        }
    }

    void doSetOsc2Wave(uint8_t wave) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].MidiOsc2Wave(wave);
        }
    }

    void doSetPwm(uint8_t pwm) {
        for (int i = 0; i < numvoices; i++) {
            mSynthVoice[i].MidiPwm(pwm);
        }
    }

    void resetTransportState() {
        recIndexStart = 0;
        recIndexEnd = 0;
        recIndex = 0;
        playIndex = 0;
        playIndexStart = 0;
        playIndexEnd = 0;
        recording = false;
        playing = false;
        playFadeRemaining = 0;
        loopRecordingAtomic.store(false);
        loopPlayingAtomic.store(false);
        loopRecordLengthAtomic.store(0);
        loopPlayIndexAtomic.store(0);
        loopPlayEndAtomic.store(0);
    }

    float nextPlaybackSample() {
        if (playIndexEnd <= 0 || playIndex >= playIndexEnd) {
            return 0.0f;
        }
        const int crossfade = std::min(crossfadeSamples, playIndexEnd / 2);
        const int crossfadeStart = playIndexEnd - crossfade;
        float sampleValue;
        if (crossfade > 0 && playIndex >= crossfadeStart) {
            const int k = playIndex - crossfadeStart;
            const float t = static_cast<float>(k + 1) /
                            static_cast<float>(crossfade);
            sampleValue = playBuffer[playIndex] * (1.0f - t) +
                          playBuffer[k] * t;
            ++playIndex;
            if (playIndex >= playIndexEnd) {
                playIndex = crossfade;
            }
        } else {
            sampleValue = playBuffer[playIndex];
            ++playIndex;
            if (playIndex >= playIndexEnd) {
                playIndex = crossfade;
            }
        }
        if (playFadeRemaining > 0 && playFadeSamples > 0) {
            sampleValue *= 1.0f -
                static_cast<float>(playFadeRemaining) /
                static_cast<float>(playFadeSamples);
            --playFadeRemaining;
        }
        return sampleValue;
    }

    void recordSample(float sampleValue) {
        if (recIndex >= recBufferLen) {
            recording = false;
            return;
        }
        recBuffer[recIndex++] = sampleValue;
        recIndexEnd = recIndex;
    }

    void preparePlaybackBuffer() {
        playIndex = 0;
        playIndexStart = 0;
        playIndexEnd = 0;
        if (recIndexEnd <= 0) {
            return;
        }

        int start = trimStart;
        if (start < 0 || start >= recIndexEnd) {
            start = 0;
        }
        int end = recIndexEnd - trimEnd;
        if (end <= start) {
            end = recIndexEnd;
        }
        int length = end - start;
        if (length <= 0) {
            return;
        }
        if (length > playBufferLen) {
            length = playBufferLen;
        }
        std::memcpy(playBuffer, &recBuffer[start], sizeof(float) * length);
        playIndexEnd = length;
    }

    void doSetPlay(bool flag) {
        if (flag && playIndexEnd <= 0) {
            preparePlaybackBuffer();
        }
        playing = flag && playIndexEnd > 0;
        if (playing) {
            playIndex = 0;
            playFadeRemaining = playFadeSamples;
        } else {
            playFadeRemaining = 0;
        }
        loopPlayingAtomic.store(playing);
        loopPlayIndexAtomic.store(playIndex);
        loopPlayEndAtomic.store(playIndexEnd);
    }

    void doSetRecord(bool flag) {
        if (flag) {
            recIndexStart = 0;
            recIndexEnd = 0;
            recIndex = 0;
            recording = true;
            playing = false;
            playIndex = 0;
            playFadeRemaining = 0;
            loopRecordingAtomic.store(true);
            loopPlayingAtomic.store(false);
            loopRecordLengthAtomic.store(0);
            loopPlayIndexAtomic.store(0);
            loopPlayEndAtomic.store(0);
        } else {
            recording = false;
            preparePlaybackBuffer();
            loopRecordingAtomic.store(false);
            loopRecordLengthAtomic.store(recIndexEnd);
            loopPlayEndAtomic.store(playIndexEnd);
        }
    }

    SynthVoice mSynthVoice[numvoices];
    int voices_notes[numvoices] = {};
    float* waveforms = nullptr;
    const float* factoryWaveforms = nullptr;
    std::atomic<uint8_t> osc1Wave{0};
    std::atomic<uint8_t> osc2Wave{0};
    oboe::ManagedStream outStream;
    std::atomic<bool> audioSuspended{false};
    std::atomic<float> dspLoad{0.0f};
    std::atomic<float> dspPeak{0.0f};
    yawn::effects::Limiter mSafetyLimiterEffect;
    std::atomic<bool> mSafetyLimiter{true};
    std::atomic<bool> mPolyphonyLeveling{true};
    float mPolyphonyGain = 1.0f;
    int kChannelCount = 2;
    int sampleRate = 48000;
    float wavedata[kWaveformSize] = {};
    int index = 0;
    float* recBuffer = nullptr;
    int recIndexStart = 0;
    int recIndexEnd = 0;
    int recIndex = 0;
    int recBufferLen = 0;
    float* playBuffer = nullptr;
    int playIndex = 0;
    int playIndexStart = 0;
    int playIndexEnd = 0;
    int playBufferLen = 0;
    bool playing = false;
    bool recording = false;
    int upSamples = 0;
    int downSamples = 0;
    float upFactor = 0.0f;
    float downFactor = 0.0f;
    float upMin = 0.0f;
    float downMin = 0.0f;
    int upTimeMs = 0;
    int downTimeMs = 0;
    int trimEnd = 0;
    int trimStart = 0;
    int crossfadeSamples = 0;
    int playFadeSamples = 0;
    int playFadeRemaining = 0;
    std::atomic<bool> loopRecordingAtomic{false};
    std::atomic<bool> loopPlayingAtomic{false};
    std::atomic<int> loopRecordLengthAtomic{0};
    std::atomic<int> loopPlayIndexAtomic{0};
    std::atomic<int> loopPlayEndAtomic{0};
    float delayLevel = 0.0f;
    Delay* delay = nullptr;
    EngineEventQueue eventQueue;
    std::mutex bankMutex;
};

#endif
