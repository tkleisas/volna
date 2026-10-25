#ifndef VAEngine_h_
#define VAEngine_h_

#include <oboe/Oboe.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>

#include "Delay.h"
#include "EngineEventQueue.h"
#include "SynthVoice.h"

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
        oboeInit();
    }

    void shutdown() {
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
        if (outStream) {
            outStream->requestStop();
        }
    }

    void resumeAudio() {
        if (!outStream) {
            oboeInit();
            return;
        }
        const oboe::StreamState state = outStream->getState();
        if (state == oboe::StreamState::Stopped ||
            state == oboe::StreamState::Paused ||
            state == oboe::StreamState::Open) {
            outStream->requestStart();
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
        oboeInit();
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* /* oboeStream */,
                                          void* audioData,
                                          int32_t numFrames) override {
        float* floatData = static_cast<float*>(audioData);
        processEvents();

        for (int i = 0; i < numFrames; ++i) {
            float sampleValue = Process();
            if (playing) {
                sampleValue += nextPlaybackSample();
            }

            wavedata[index] = sampleValue;
            index = (index + 1) % 256;

            for (int j = 0; j < kChannelCount; j++) {
                floatData[i * kChannelCount + j] = sampleValue;
            }

            if (recording) {
                recordSample(sampleValue);
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
    }

    float nextPlaybackSample() {
        if (playIndexEnd <= 0 || playIndex >= playIndexEnd) {
            return 0.0f;
        }

        float gain = 1.0f;
        if (upSamples > 0 && playIndex < upSamples) {
            gain = static_cast<float>(playIndex) / static_cast<float>(upSamples);
        }
        if (downSamples > 0) {
            const int remaining = playIndexEnd - playIndex;
            if (remaining < downSamples) {
                const float downGain =
                    static_cast<float>(remaining) / static_cast<float>(downSamples);
                gain = std::min(gain, downGain);
            }
        }

        const float sampleValue = playBuffer[playIndex] * gain;
        ++playIndex;
        if (playIndex >= playIndexEnd) {
            playIndex = 0;
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
        }
    }

    void doSetRecord(bool flag) {
        if (flag) {
            recIndexStart = 0;
            recIndexEnd = 0;
            recIndex = 0;
            recording = true;
        } else {
            recording = false;
            preparePlaybackBuffer();
        }
    }

    SynthVoice mSynthVoice[numvoices];
    int voices_notes[numvoices] = {};
    float* waveforms = nullptr;
    const float* factoryWaveforms = nullptr;
    std::atomic<uint8_t> osc1Wave{0};
    std::atomic<uint8_t> osc2Wave{0};
    oboe::ManagedStream outStream;
    int kChannelCount = 2;
    int sampleRate = 48000;
    float wavedata[256] = {};
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
    float delayLevel = 0.0f;
    Delay* delay = nullptr;
    EngineEventQueue eventQueue;
    std::mutex bankMutex;
};

#endif
