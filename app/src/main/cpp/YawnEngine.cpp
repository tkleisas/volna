#include "YawnEngine.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "effects/Chorus.h"
#include "effects/Compressor.h"
#include "effects/Delay.h"
#include "effects/Distortion.h"
#include "effects/EQ.h"
#include "effects/Filter.h"
#include "effects/Limiter.h"
#include "effects/Reverb.h"
#include "instruments/SubtractiveSynth.h"
#include "instruments/WavetableSynth.h"
#include "midi/Arpeggiator.h"
#include "midi/Chord.h"
#include "midi/LFO.h"
#include "midi/MidiPitch.h"
#include "midi/MidiRandom.h"
#include "midi/NoteLength.h"
#include "midi/Scale.h"
#include "midi/VelocityEffect.h"
#include "util/Logger.h"

namespace {
constexpr float kCcTimeMax = 5.0f;

float ccToSeconds(int value) {
    const float t = static_cast<float>(std::clamp(value, 0, 127)) / 127.0f;
    return 0.001f + t * t * kCcTimeMax;
}

float ccToUnit(int value) {
    return static_cast<float>(std::clamp(value, 0, 127)) / 127.0f;
}

float clampParam(const yawn::ParameterInfo& info, float value) {
    return std::clamp(value, info.minValue, info.maxValue);
}
} // namespace

YawnEngine::YawnEngine() {
    mMetadataInstruments[0] = std::make_unique<yawn::instruments::WavetableSynth>();
    mMetadataInstruments[1] = std::make_unique<yawn::instruments::SubtractiveSynth>();

    mMidiEffects[0] = std::make_unique<yawn::midi::Arpeggiator>();
    mMidiEffects[1] = std::make_unique<yawn::midi::Chord>();
    mMidiEffects[2] = std::make_unique<yawn::midi::Scale>();
    mMidiEffects[3] = std::make_unique<yawn::midi::NoteLength>();
    mMidiEffects[4] = std::make_unique<yawn::midi::VelocityEffect>();
    mMidiEffects[5] = std::make_unique<yawn::midi::MidiRandom>();
    mMidiEffects[6] = std::make_unique<yawn::midi::MidiPitch>();
    mMidiEffects[7] = std::make_unique<yawn::midi::LFO>();

    mAudioEffects[0] = std::make_unique<yawn::effects::Delay>();
    mAudioEffects[1] = std::make_unique<yawn::effects::Chorus>();
    mAudioEffects[2] = std::make_unique<yawn::effects::Reverb>();
    mAudioEffects[3] = std::make_unique<yawn::effects::Filter>();
    mAudioEffects[4] = std::make_unique<yawn::effects::Distortion>();
    mAudioEffects[5] = std::make_unique<yawn::effects::EQ>();
    mAudioEffects[6] = std::make_unique<yawn::effects::Compressor>();
    mAudioEffects[7] = std::make_unique<yawn::effects::Limiter>();

    for (auto& bypass : mMidiBypass) bypass.store(true);
    for (auto& bypass : mAudioBypass) bypass.store(true);

    for (int fx = 0; fx < kMidiEffectCount; ++fx) {
        for (int p = 0; p < kMaxParams; ++p) mMidiParams[fx].values[p].store(0.0f);
    }
    for (int fx = 0; fx < kAudioEffectCount; ++fx) {
        for (int p = 0; p < kMaxParams; ++p) mAudioParams[fx].values[p].store(0.0f);
    }
    for (int p = 0; p < kMaxParams; ++p) mInstrumentParams.values[p].store(0.0f);
}

YawnEngine::~YawnEngine() {
    shutdown();
}

void YawnEngine::init(float sampleRate, int instrumentIndex) {
    shutdown();
    mSampleRate = sampleRate > 0.0f ? static_cast<int>(sampleRate) : 48000;
    mSamplesPerBeat = static_cast<double>(mSampleRate) * 60.0 / 120.0;
    mTransport.sampleRate = mSampleRate;
    mTransport.samplesPerBeat = mSamplesPerBeat;
    mTransport.bpm = 120.0;
    mTransport.beatsPerBar = 4;
    mTransport.playing = true;
    mBeatPosition = 0.0;
    mSamplePosition = 0;

    mMixBuffer.assign(static_cast<size_t>(kMaxFrames) * 2, 0.0f);
    mRecordCapacity = mSampleRate * 60;
    mRecordBuffer.assign(static_cast<size_t>(mRecordCapacity) * 2, 0.0f);
    mRecordLength = 0;
    mPlayStart = 0;
    mPlayEnd = 0;
    mPlayIndex = 0;
    mRecording = false;
    mPlayingLoop = false;
    mFadeSamples = mSampleRate / 100; // 10 ms fade

    mInstrumentIndex = std::clamp(instrumentIndex, 0, kInstrumentCount - 1);
    mInstrument = createInstrument(mInstrumentIndex);
    applyInstrumentDefaults();
    initMidiEffects();
    initAudioEffects();

    if (mInstrument) {
        mInstrument->init(mSampleRate, kMaxFrames);
        for (int p = 0; p < mInstrument->parameterCount(); ++p) {
            mInstrument->setParameter(p, mInstrumentParams.values[p].load());
        }
    }

    mMidiBuffer.clear();
    mEvents.clear();
    mWaveformIndex = 0;
    std::fill(std::begin(mWaveform), std::end(mWaveform), 0.0f);

    mInitialized.store(true);
    oboeInit();
}

void YawnEngine::shutdown() {
    mInitialized.store(false);
    if (mStream) {
        mStream.reset();
    }
    if (mInstrument) {
        mInstrument->reset();
    }
    mRecording = false;
    mPlayingLoop = false;
}

void YawnEngine::oboeInit() {
    oboe::AudioStreamBuilder builder;
    builder.setSharingMode(oboe::SharingMode::Exclusive)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setDirection(oboe::Direction::Output)
        ->setChannelCount(mChannelCount)
        ->setSampleRate(mSampleRate)
        ->setFormat(oboe::AudioFormat::Float)
        ->setCallback(this);

    const oboe::Result result = builder.openManagedStream(mStream);
    if (result != oboe::Result::OK) {
        mStream.reset();
        LOG_ERROR("Volna", "Could not open YAWN engine audio stream");
        return;
    }
    mStream->requestStart();
}

bool YawnEngine::audioIsRunning() const {
    if (!mStream) return false;
    const oboe::StreamState state = mStream->getState();
    return state == oboe::StreamState::Starting ||
           state == oboe::StreamState::Started;
}

void YawnEngine::pauseAudio() {
    if (mStream) mStream->requestStop();
}

void YawnEngine::resumeAudio() {
    if (!mStream) {
        if (mInitialized.load()) oboeInit();
        return;
    }
    const oboe::StreamState state = mStream->getState();
    if (state == oboe::StreamState::Stopped ||
        state == oboe::StreamState::Paused ||
        state == oboe::StreamState::Open) {
        mStream->requestStart();
    }
}

std::unique_ptr<yawn::instruments::Instrument> YawnEngine::createInstrument(
    int index) const {
    switch (index) {
        case static_cast<int>(InstrumentKind::Subtractive):
            return std::make_unique<yawn::instruments::SubtractiveSynth>();
        case static_cast<int>(InstrumentKind::Wavetable):
        default:
            return std::make_unique<yawn::instruments::WavetableSynth>();
    }
}

void YawnEngine::applyInstrumentDefaults() {
    if (!mInstrument) return;
    const int count = std::min(mInstrument->parameterCount(), kMaxParams);
    for (int p = 0; p < count; ++p) {
        const auto& info = mInstrument->parameterInfo(p);
        mInstrumentParams.values[p].store(info.defaultValue);
    }
}

void YawnEngine::initMidiEffects() {
    for (int fx = 0; fx < kMidiEffectCount; ++fx) {
        auto& effect = mMidiEffects[fx];
        if (!effect) continue;
        effect->init(mSampleRate);
        effect->setBypassed(mMidiBypass[fx].load());
        const int count = std::min(effect->parameterCount(), kMaxParams);
        for (int p = 0; p < count; ++p) {
            const float value = effect->parameterInfo(p).defaultValue;
            mMidiParams[fx].values[p].store(value);
            effect->setParameter(p, value);
        }
    }
}

void YawnEngine::initAudioEffects() {
    for (int fx = 0; fx < kAudioEffectCount; ++fx) {
        auto& effect = mAudioEffects[fx];
        if (!effect) continue;
        effect->init(mSampleRate, kMaxFrames);
        effect->setBypassed(mAudioBypass[fx].load());
        const int count = std::min(effect->parameterCount(), kMaxParams);
        for (int p = 0; p < count; ++p) {
            const float value = effect->parameterInfo(p).defaultValue;
            mAudioParams[fx].values[p].store(value);
            effect->setParameter(p, value);
        }
    }
}

bool YawnEngine::selectInstrument(int index) {
    index = std::clamp(index, 0, kInstrumentCount - 1);
    std::lock_guard<std::mutex> lock(mControlMutex);
    if (index == mInstrumentIndex && mInstrument) {
        return true;
    }
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();

    mInstrumentIndex = index;
    mInstrument = createInstrument(index);
    applyInstrumentDefaults();
    if (mInstrument) {
        mInstrument->init(mSampleRate, kMaxFrames);
        for (int p = 0; p < mInstrument->parameterCount(); ++p) {
            mInstrument->setParameter(p, mInstrumentParams.values[p].load());
        }
        auto* wavetable =
            dynamic_cast<yawn::instruments::WavetableSynth*>(mInstrument.get());
        if (wavetable && !mWavetablePath.empty()) {
            std::string error;
            wavetable->loadCustomWavetable(mWavetablePath, &error);
            mInstrumentParams.values[0].store(
                static_cast<float>(yawn::instruments::WavetableSynth::kCustomTable));
        }
    }
    for (auto& effect : mMidiEffects) effect->reset();
    for (auto& effect : mAudioEffects) effect->reset();
    mEvents.clear();
    mMidiBuffer.clear();

    if (wasRunning && mStream) mStream->requestStart();
    return true;
}

const char* YawnEngine::instrumentName(int index) {
    static const char* kNames[] = {"YAWN Wavetable", "YAWN Subtractive"};
    if (index < 0 || index >= kInstrumentCount) return "";
    return kNames[index];
}

const char* YawnEngine::instrumentId(int index) {
    static const char* kIds[] = {"wavetable", "subsynth"};
    if (index < 0 || index >= kInstrumentCount) return "";
    return kIds[index];
}

const yawn::ParameterInfo* YawnEngine::instrumentInfo(int index) const {
    if (!mMetadataInstruments[std::clamp(mInstrumentIndex, 0, kInstrumentCount - 1)])
        return nullptr;
    const auto& instrument =
        *mMetadataInstruments[std::clamp(mInstrumentIndex, 0, kInstrumentCount - 1)];
    if (index < 0 || index >= instrument.parameterCount()) return nullptr;
    return &instrument.parameterInfo(index);
}

int YawnEngine::instrumentParamCount() const {
    const auto& instrument =
        *mMetadataInstruments[std::clamp(mInstrumentIndex, 0, kInstrumentCount - 1)];
    return instrument.parameterCount();
}

const char* YawnEngine::instrumentParamName(int index) const {
    const auto* info = instrumentInfo(index);
    return info ? info->name : "";
}

float YawnEngine::instrumentParamMin(int index) const {
    const auto* info = instrumentInfo(index);
    return info ? info->minValue : 0.0f;
}

float YawnEngine::instrumentParamMax(int index) const {
    const auto* info = instrumentInfo(index);
    return info ? info->maxValue : 0.0f;
}

float YawnEngine::instrumentParamDefault(int index) const {
    const auto* info = instrumentInfo(index);
    return info ? info->defaultValue : 0.0f;
}

bool YawnEngine::instrumentParamIsBoolean(int index) const {
    const auto* info = instrumentInfo(index);
    return info && info->isBoolean;
}

int YawnEngine::instrumentParamLabelCount(int index) const {
    const auto* info = instrumentInfo(index);
    return info ? info->valueLabelCount : 0;
}

const char* YawnEngine::instrumentParamLabel(int index, int label) const {
    const auto* info = instrumentInfo(index);
    if (!info || !info->valueLabels || label < 0 || label >= info->valueLabelCount)
        return "";
    return info->valueLabels[label];
}

float YawnEngine::instrumentParamValue(int index) const {
    if (index < 0 || index >= kMaxParams) return 0.0f;
    return mInstrumentParams.values[index].load();
}

void YawnEngine::setInstrumentParam(int index, float value) {
    const auto* info = instrumentInfo(index);
    if (!info) return;
    const float clamped = clampParam(*info, value);
    mInstrumentParams.values[index].store(clamped);
    EngineEvent event;
    event.type = EngineEvent::Type::SetInstrumentParam;
    event.i = index;
    event.f = clamped;
    push(event);
}

const char* YawnEngine::midiEffectName(int index) {
    static const char* kNames[] = {"Arpeggiator", "Chord", "Scale",
                                   "Note Length", "Velocity", "Random",
                                   "Pitch", "LFO"};
    if (index < 0 || index >= kMidiEffectCount) return "";
    return kNames[index];
}

const char* YawnEngine::midiEffectId(int index) {
    static const char* kIds[] = {"arp", "chord", "scale", "notelength",
                                 "velocity", "random", "pitch", "lfo"};
    if (index < 0 || index >= kMidiEffectCount) return "";
    return kIds[index];
}

const yawn::ParameterInfo* YawnEngine::midiInfo(int effect, int index) const {
    if (effect < 0 || effect >= kMidiEffectCount || !mMidiEffects[effect])
        return nullptr;
    if (index < 0 || index >= mMidiEffects[effect]->parameterCount())
        return nullptr;
    return &mMidiEffects[effect]->parameterInfo(index);
}

int YawnEngine::midiEffectParamCount(int effect) const {
    if (effect < 0 || effect >= kMidiEffectCount || !mMidiEffects[effect])
        return 0;
    return mMidiEffects[effect]->parameterCount();
}

const char* YawnEngine::midiEffectParamName(int effect, int index) const {
    const auto* info = midiInfo(effect, index);
    return info ? info->name : "";
}

float YawnEngine::midiEffectParamMin(int effect, int index) const {
    const auto* info = midiInfo(effect, index);
    return info ? info->minValue : 0.0f;
}

float YawnEngine::midiEffectParamMax(int effect, int index) const {
    const auto* info = midiInfo(effect, index);
    return info ? info->maxValue : 0.0f;
}

float YawnEngine::midiEffectParamDefault(int effect, int index) const {
    const auto* info = midiInfo(effect, index);
    return info ? info->defaultValue : 0.0f;
}

bool YawnEngine::midiEffectParamIsBoolean(int effect, int index) const {
    const auto* info = midiInfo(effect, index);
    return info && info->isBoolean;
}

int YawnEngine::midiEffectParamLabelCount(int effect, int index) const {
    const auto* info = midiInfo(effect, index);
    return info ? info->valueLabelCount : 0;
}

const char* YawnEngine::midiEffectParamLabel(int effect, int index,
                                             int label) const {
    const auto* info = midiInfo(effect, index);
    if (!info || !info->valueLabels || label < 0 ||
        label >= info->valueLabelCount)
        return "";
    return info->valueLabels[label];
}

float YawnEngine::midiEffectParamValue(int effect, int index) const {
    if (effect < 0 || effect >= kMidiEffectCount || index < 0 ||
        index >= kMaxParams)
        return 0.0f;
    return mMidiParams[effect].values[index].load();
}

void YawnEngine::setMidiEffectParam(int effect, int index, float value) {
    const auto* info = midiInfo(effect, index);
    if (!info) return;
    const float clamped = clampParam(*info, value);
    mMidiParams[effect].values[index].store(clamped);
    EngineEvent event;
    event.type = EngineEvent::Type::SetMidiEffectParam;
    event.i = effect;
    event.j = index;
    event.f = clamped;
    push(event);
}

bool YawnEngine::midiEffectBypassed(int index) const {
    if (index < 0 || index >= kMidiEffectCount) return true;
    return mMidiBypass[index].load();
}

void YawnEngine::setMidiEffectBypassed(int index, bool bypassed) {
    if (index < 0 || index >= kMidiEffectCount) return;
    mMidiBypass[index].store(bypassed);
    EngineEvent event;
    event.type = EngineEvent::Type::SetMidiEffectBypass;
    event.i = index;
    event.a = bypassed ? 1 : 0;
    push(event);
}

const char* YawnEngine::audioEffectName(int index) {
    static const char* kNames[] = {"Delay", "Chorus", "Reverb", "Filter",
                                   "Distortion", "EQ", "Compressor", "Limiter"};
    if (index < 0 || index >= kAudioEffectCount) return "";
    return kNames[index];
}

const char* YawnEngine::audioEffectId(int index) {
    static const char* kIds[] = {"delay", "chorus", "reverb", "filter",
                                 "distortion", "eq", "compressor", "limiter"};
    if (index < 0 || index >= kAudioEffectCount) return "";
    return kIds[index];
}

const yawn::ParameterInfo* YawnEngine::audioInfo(int effect, int index) const {
    if (effect < 0 || effect >= kAudioEffectCount || !mAudioEffects[effect])
        return nullptr;
    if (index < 0 || index >= mAudioEffects[effect]->parameterCount())
        return nullptr;
    return &mAudioEffects[effect]->parameterInfo(index);
}

int YawnEngine::audioEffectParamCount(int effect) const {
    if (effect < 0 || effect >= kAudioEffectCount || !mAudioEffects[effect])
        return 0;
    return mAudioEffects[effect]->parameterCount();
}

const char* YawnEngine::audioEffectParamName(int effect, int index) const {
    const auto* info = audioInfo(effect, index);
    return info ? info->name : "";
}

float YawnEngine::audioEffectParamMin(int effect, int index) const {
    const auto* info = audioInfo(effect, index);
    return info ? info->minValue : 0.0f;
}

float YawnEngine::audioEffectParamMax(int effect, int index) const {
    const auto* info = audioInfo(effect, index);
    return info ? info->maxValue : 0.0f;
}

float YawnEngine::audioEffectParamDefault(int effect, int index) const {
    const auto* info = audioInfo(effect, index);
    return info ? info->defaultValue : 0.0f;
}

bool YawnEngine::audioEffectParamIsBoolean(int effect, int index) const {
    const auto* info = audioInfo(effect, index);
    return info && info->isBoolean;
}

int YawnEngine::audioEffectParamLabelCount(int effect, int index) const {
    const auto* info = audioInfo(effect, index);
    return info ? info->valueLabelCount : 0;
}

const char* YawnEngine::audioEffectParamLabel(int effect, int index,
                                              int label) const {
    const auto* info = audioInfo(effect, index);
    if (!info || !info->valueLabels || label < 0 ||
        label >= info->valueLabelCount)
        return "";
    return info->valueLabels[label];
}

float YawnEngine::audioEffectParamValue(int effect, int index) const {
    if (effect < 0 || effect >= kAudioEffectCount || index < 0 ||
        index >= kMaxParams)
        return 0.0f;
    return mAudioParams[effect].values[index].load();
}

void YawnEngine::setAudioEffectParam(int effect, int index, float value) {
    const auto* info = audioInfo(effect, index);
    if (!info) return;
    const float clamped = clampParam(*info, value);
    mAudioParams[effect].values[index].store(clamped);
    EngineEvent event;
    event.type = EngineEvent::Type::SetAudioEffectParam;
    event.i = effect;
    event.j = index;
    event.f = clamped;
    push(event);
}

bool YawnEngine::audioEffectBypassed(int index) const {
    if (index < 0 || index >= kAudioEffectCount) return true;
    return mAudioBypass[index].load();
}

void YawnEngine::setAudioEffectBypassed(int index, bool bypassed) {
    if (index < 0 || index >= kAudioEffectCount) return;
    mAudioBypass[index].store(bypassed);
    EngineEvent event;
    event.type = EngineEvent::Type::SetAudioEffectBypass;
    event.i = index;
    event.a = bypassed ? 1 : 0;
    push(event);
}

void YawnEngine::setTempo(float bpm) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetTempo;
    event.f = std::clamp(bpm, 20.0f, 300.0f);
    push(event);
}

void YawnEngine::setMasterVolume(float volume) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetMasterVolume;
    event.f = std::clamp(volume, 0.0f, 2.0f);
    push(event);
}

void YawnEngine::push(const EngineEvent& event) {
    mEvents.push(event);
}

void YawnEngine::noteOn(int channel, int note, int velocity) {
    EngineEvent event;
    event.type = EngineEvent::Type::NoteOn;
    event.channel = static_cast<uint8_t>(channel);
    event.a = static_cast<uint8_t>(note);
    event.b = static_cast<uint8_t>(velocity);
    push(event);
}

void YawnEngine::noteOff(int channel, int note, int velocity) {
    EngineEvent event;
    event.type = EngineEvent::Type::NoteOff;
    event.channel = static_cast<uint8_t>(channel);
    event.a = static_cast<uint8_t>(note);
    event.b = static_cast<uint8_t>(velocity);
    push(event);
}

void YawnEngine::pitchBend(int channel, int lsb, int msb) {
    EngineEvent event;
    event.type = EngineEvent::Type::PitchBend;
    event.channel = static_cast<uint8_t>(channel);
    event.a = static_cast<uint8_t>(lsb);
    event.b = static_cast<uint8_t>(msb);
    push(event);
}

void YawnEngine::controlChange(int channel, int control, int value) {
    EngineEvent event;
    event.type = EngineEvent::Type::ControlChange;
    event.channel = static_cast<uint8_t>(channel);
    event.a = static_cast<uint8_t>(control);
    event.b = static_cast<uint8_t>(value);
    push(event);
}

void YawnEngine::polyPressure(int channel, int note, int pressure) {
    EngineEvent event;
    event.type = EngineEvent::Type::PolyPressure;
    event.channel = static_cast<uint8_t>(channel);
    event.a = static_cast<uint8_t>(note);
    event.b = static_cast<uint8_t>(pressure);
    push(event);
}

void YawnEngine::channelPressure(int channel, int pressure) {
    EngineEvent event;
    event.type = EngineEvent::Type::ChannelPressure;
    event.channel = static_cast<uint8_t>(channel);
    event.a = static_cast<uint8_t>(pressure);
    push(event);
}

void YawnEngine::allNotesOff() {
    EngineEvent event;
    event.type = EngineEvent::Type::AllNotesOff;
    push(event);
}

void YawnEngine::selectWaveform(int /* channel */, int osc, int /* note */,
                                int wave) {
    if (!mInstrument) return;
    const float normalized =
        static_cast<float>(std::clamp(wave, 0, 255)) / 255.0f;
    if (mInstrumentIndex == static_cast<int>(InstrumentKind::Wavetable)) {
        setInstrumentParam(yawn::instruments::WavetableSynth::kPosition,
                           normalized);
    } else {
        if (osc == 0) {
            setInstrumentParam(yawn::instruments::SubtractiveSynth::kFilterCutoff,
                               normalized);
        }
    }
}

void YawnEngine::noteSpread(int /* channel */, int /* note */, int spread) {
    const float amount =
        static_cast<float>(std::clamp(spread, 0, 127) - 63) / 63.0f;
    if (mInstrumentIndex == static_cast<int>(InstrumentKind::Wavetable)) {
        setInstrumentParam(yawn::instruments::WavetableSynth::kUnisonDetune,
                           std::abs(amount));
    } else {
        setInstrumentParam(yawn::instruments::SubtractiveSynth::kOsc2Detune,
                           amount);
    }
}

void YawnEngine::setPlay(bool play) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetPlay;
    event.a = play ? 1 : 0;
    push(event);
}

void YawnEngine::setRecord(bool record) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetRecord;
    event.a = record ? 1 : 0;
    push(event);
}

void YawnEngine::setDelayLevel(int level) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetDelayLevel;
    event.f = std::clamp(level, 0, 255) / 255.0f;
    push(event);
}

void YawnEngine::setDelayTime(int samples) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetDelayLength;
    event.i = samples;
    push(event);
}

void YawnEngine::setDelayFeedback(int feedback) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetDelayFeedback;
    event.f = 0.99f * static_cast<float>(std::clamp(feedback, 0, 255)) / 255.0f;
    push(event);
}

void YawnEngine::processEvents(int /* numFrames */) {
    EngineEvent event;
    while (mEvents.pop(event)) {
        dispatchEvent(event, kMaxFrames);
    }
}

void YawnEngine::dispatchEvent(const EngineEvent& event, int numFrames) {
    switch (event.type) {
        case EngineEvent::Type::NoteOn:
            handleNoteOn(event.channel, event.a, event.b);
            break;
        case EngineEvent::Type::NoteOff:
            handleNoteOff(event.channel, event.a);
            break;
        case EngineEvent::Type::NoteTransition:
            handleNoteOff(event.channel, event.a);
            handleNoteOn(event.channel, event.b, event.c);
            break;
        case EngineEvent::Type::NoteSpread:
            noteSpread(event.channel, event.a, event.b);
            break;
        case EngineEvent::Type::PitchBend:
            handlePitchBend(event.channel, (event.b << 7) | event.a);
            break;
        case EngineEvent::Type::ControlChange:
            handleControlChange(event.channel, event.a, event.b);
            break;
        case EngineEvent::Type::PolyPressure:
            if (mInstrument) {
                if (mInstrumentIndex ==
                    static_cast<int>(InstrumentKind::Wavetable)) {
                    setInstrumentParam(
                        yawn::instruments::WavetableSynth::kPosition,
                        static_cast<float>(event.b) / 127.0f);
                } else {
                    setInstrumentParam(
                        yawn::instruments::SubtractiveSynth::kFilterCutoff,
                        static_cast<float>(event.b) / 127.0f);
                }
            }
            mMidiBuffer.addMessage(yawn::midi::MidiMessage::polyPressure(
                event.channel, event.a, event.b));
            break;
        case EngineEvent::Type::ChannelPressure:
            mMidiBuffer.addMessage(yawn::midi::MidiMessage::channelPressure(
                event.channel, event.a));
            break;
        case EngineEvent::Type::AllNotesOff:
            handleAllNotesOff();
            break;
        case EngineEvent::Type::SelectWaveform:
            selectWaveform(event.channel, event.a, event.b, event.c);
            break;
        case EngineEvent::Type::SetTet:
        case EngineEvent::Type::SetTune:
        case EngineEvent::Type::SetOctaveFactor:
        case EngineEvent::Type::SetPwm:
            break;
        case EngineEvent::Type::SetOsc1Wave:
            selectWaveform(0, 0, 0, event.a);
            break;
        case EngineEvent::Type::SetOsc2Wave:
            selectWaveform(0, 1, 0, event.a);
            break;
        case EngineEvent::Type::SetAdsr: {
            const int base = mInstrumentIndex ==
                                     static_cast<int>(InstrumentKind::Wavetable)
                                 ? yawn::instruments::WavetableSynth::kAmpAttack
                                 : yawn::instruments::SubtractiveSynth::kAmpAttack;
            setInstrumentParam(base + 0, ccToSeconds(event.a));
            setInstrumentParam(base + 1, ccToSeconds(event.b));
            setInstrumentParam(base + 2, ccToUnit(event.c));
            setInstrumentParam(base + 3, ccToSeconds(event.d));
            break;
        }
        case EngineEvent::Type::SetInstrumentParam:
            if (mInstrument) mInstrument->setParameter(event.i, event.f);
            break;
        case EngineEvent::Type::SetMidiEffectParam:
            if (event.i >= 0 && event.i < kMidiEffectCount &&
                mMidiEffects[event.i]) {
                mMidiEffects[event.i]->setParameter(event.j, event.f);
            }
            break;
        case EngineEvent::Type::SetMidiEffectBypass:
            if (event.i >= 0 && event.i < kMidiEffectCount &&
                mMidiEffects[event.i]) {
                mMidiEffects[event.i]->setBypassed(event.a != 0);
            }
            break;
        case EngineEvent::Type::SetAudioEffectParam:
            if (event.i >= 0 && event.i < kAudioEffectCount &&
                mAudioEffects[event.i]) {
                mAudioEffects[event.i]->setParameter(event.j, event.f);
            }
            break;
        case EngineEvent::Type::SetAudioEffectBypass:
            if (event.i >= 0 && event.i < kAudioEffectCount &&
                mAudioEffects[event.i]) {
                mAudioEffects[event.i]->setBypassed(event.a != 0);
            }
            break;
        case EngineEvent::Type::SetTempo:
            mTransport.bpm = event.f;
            mSamplesPerBeat = static_cast<double>(mSampleRate) * 60.0 / event.f;
            mTransport.samplesPerBeat = mSamplesPerBeat;
            break;
        case EngineEvent::Type::SetMasterVolume:
            mMasterVolume = event.f;
            break;
        case EngineEvent::Type::SetPlay:
            doSetPlay(event.a != 0);
            break;
        case EngineEvent::Type::SetRecord:
            doSetRecord(event.a != 0);
            break;
        case EngineEvent::Type::SetDelayLevel:
            setAudioEffectParam(0, yawn::effects::Delay::kWetDry, event.f);
            setAudioEffectBypassed(0, event.f <= 0.001f);
            break;
        case EngineEvent::Type::SetDelayLength: {
            const float ms = mSampleRate > 0
                                 ? 1000.0f * static_cast<float>(event.i) /
                                       static_cast<float>(mSampleRate)
                                 : 500.0f;
            setAudioEffectParam(0, yawn::effects::Delay::kTimeMs,
                                std::clamp(ms, 1.0f, 4000.0f));
            setAudioEffectParam(0, yawn::effects::Delay::kSync, 0.0f);
            break;
        }
        case EngineEvent::Type::SetDelayFeedback:
            setAudioEffectParam(0, yawn::effects::Delay::kFeedback, event.f);
            break;
        default:
            break;
    }
}

void YawnEngine::handleNoteOn(int channel, int note, int velocity) {
    mMidiBuffer.addMessage(
        yawn::midi::MidiMessage::noteOn(channel, note, velocity));
}

void YawnEngine::handleNoteOff(int channel, int note) {
    mMidiBuffer.addMessage(yawn::midi::MidiMessage::noteOff(channel, note));
}

void YawnEngine::handlePitchBend(int channel, int value14) {
    mMidiBuffer.addMessage(yawn::midi::MidiMessage::pitchBend(
        channel, static_cast<uint16_t>(std::clamp(value14, 0, 16383))));
}

void YawnEngine::handleControlChange(int channel, int control, int value) {
    mMidiBuffer.addMessage(
        yawn::midi::MidiMessage::cc(channel, control, value));

    if (!mInstrument) return;
    const bool isWavetable =
        mInstrumentIndex == static_cast<int>(InstrumentKind::Wavetable);
    switch (control) {
        case 16:
            if (isWavetable) {
                setInstrumentParam(yawn::instruments::WavetableSynth::kPosition,
                                   static_cast<float>(value) / 255.0f);
            } else {
                setInstrumentParam(
                    yawn::instruments::SubtractiveSynth::kOsc1Wave,
                    std::round((static_cast<float>(value) / 255.0f) * 4.0f));
            }
            break;
        case 17:
            if (!isWavetable) {
                setInstrumentParam(
                    yawn::instruments::SubtractiveSynth::kOsc2Wave,
                    std::round((static_cast<float>(value) / 255.0f) * 4.0f));
            }
            break;
        case 18:
        case 19:
        case 20:
        case 21: {
            const int param = isWavetable
                                  ? yawn::instruments::WavetableSynth::kAmpAttack +
                                        (control - 18)
                                  : yawn::instruments::SubtractiveSynth::kAmpAttack +
                                        (control - 18);
            const int sustain = isWavetable
                                    ? yawn::instruments::WavetableSynth::kAmpSustain
                                    : yawn::instruments::SubtractiveSynth::kAmpSustain;
            setInstrumentParam(param, control == 20 || param == sustain
                                          ? ccToUnit(value)
                                          : ccToSeconds(value));
            break;
        }
        case 22:
        case 23:
        case 24:
        case 25: {
            const int param = isWavetable
                                  ? yawn::instruments::WavetableSynth::kFiltAttack +
                                        (control - 22)
                                  : yawn::instruments::SubtractiveSynth::kFiltAttack +
                                        (control - 22);
            const int sustain = isWavetable
                                    ? yawn::instruments::WavetableSynth::kFiltSustain
                                    : yawn::instruments::SubtractiveSynth::kFiltSustain;
            setInstrumentParam(param, control == 24 || param == sustain
                                          ? ccToUnit(value)
                                          : ccToSeconds(value));
            break;
        }
        case 1:
            if (isWavetable) {
                setInstrumentParam(yawn::instruments::WavetableSynth::kLFOAmount,
                                   ccToUnit(value));
            } else {
                setInstrumentParam(yawn::instruments::SubtractiveSynth::kLFODepth,
                                   ccToUnit(value));
            }
            break;
        case 74:
            if (isWavetable) {
                setInstrumentParam(yawn::instruments::WavetableSynth::kPosition,
                                   ccToUnit(value));
            } else {
                setInstrumentParam(
                    yawn::instruments::SubtractiveSynth::kFilterCutoff,
                    ccToUnit(value));
            }
            break;
        case 123:
            handleAllNotesOff();
            break;
        default:
            break;
    }
}

void YawnEngine::handleAllNotesOff() {
    mMidiBuffer.addMessage(yawn::midi::MidiMessage::cc(0, 123, 0));
}

void YawnEngine::doSetPlay(bool play) {
    if (play && mRecordLength > 0) {
        mPlayStart = 0;
        mPlayEnd = mRecordLength;
        mPlayIndex = mPlayStart;
        mPlayingLoop = true;
    } else {
        mPlayingLoop = false;
    }
}

void YawnEngine::doSetRecord(bool record) {
    if (record) {
        mRecordLength = 0;
        mPlayIndex = 0;
        mPlayingLoop = false;
        mRecording = true;
    } else {
        mRecording = false;
    }
}

void YawnEngine::renderLoop(float* left, float* right, int numFrames) {
    if (!mPlayingLoop || mPlayEnd <= mPlayStart) return;
    for (int i = 0; i < numFrames; ++i) {
        if (mPlayIndex >= mPlayEnd) mPlayIndex = mPlayStart;
        float gain = 1.0f;
        if (mFadeSamples > 0) {
            const int fromStart = mPlayIndex - mPlayStart;
            const int fromEnd = mPlayEnd - mPlayIndex;
            if (fromStart < mFadeSamples) {
                gain = static_cast<float>(fromStart) / mFadeSamples;
            }
            if (fromEnd < mFadeSamples) {
                gain = std::min(gain,
                                static_cast<float>(fromEnd) / mFadeSamples);
            }
        }
        const int base = mPlayIndex * 2;
        left[i * 2] += mRecordBuffer[base] * gain;
        right[i * 2] += mRecordBuffer[base + 1] * gain;
        ++mPlayIndex;
        if (mPlayIndex >= mPlayEnd) mPlayIndex = mPlayStart;
    }
}

void YawnEngine::recordSamples(const float* left, const float* right,
                               int numFrames) {
    if (!mRecording) return;
    for (int i = 0; i < numFrames; ++i) {
        if (mRecordLength >= mRecordCapacity) {
            mRecording = false;
            return;
        }
        const int base = mRecordLength * 2;
        mRecordBuffer[base] = left[i * 2];
        mRecordBuffer[base + 1] = right[i * 2];
        ++mRecordLength;
    }
}

oboe::DataCallbackResult YawnEngine::onAudioReady(
    oboe::AudioStream* /* stream */, void* audioData, int32_t numFrames) {
    if (numFrames <= 0) return oboe::DataCallbackResult::Continue;

    float* output = static_cast<float*>(audioData);
    processEvents(numFrames);

    int remaining = numFrames;
    int offset = 0;
    bool firstChunk = true;
    while (remaining > 0) {
        const int frames = std::min(remaining, kMaxFrames);
        mTransport.positionInBeats = mBeatPosition;
        mTransport.positionInSamples = mSamplePosition;

        if (!firstChunk) {
            mMidiBuffer.clear();
        }

        for (int fx = 0; fx < kMidiEffectCount; ++fx) {
            if (!mMidiBypass[fx].load() && mMidiEffects[fx]) {
                mMidiEffects[fx]->process(mMidiBuffer, frames, mTransport);
            }
        }

        std::fill(mMixBuffer.begin(), mMixBuffer.begin() +
                  static_cast<size_t>(frames) * 2, 0.0f);
        float* left = mMixBuffer.data();
        float* right = mMixBuffer.data() + 1;

        if (mInstrument) {
            mInstrument->process(mMixBuffer.data(), frames, 2, mMidiBuffer);
        }
        for (int fx = 0; fx < kAudioEffectCount; ++fx) {
            if (!mAudioBypass[fx].load() && mAudioEffects[fx]) {
                mAudioEffects[fx]->process(mMixBuffer.data(), frames, 2);
            }
        }

        renderLoop(left, right, frames);

        for (int i = 0; i < frames; ++i) {
            const float l = std::tanh(left[i * 2] * mMasterVolume);
            const float r = std::tanh(right[i * 2] * mMasterVolume);
            if (mChannelCount > 1) {
                output[(offset + i) * mChannelCount] = l;
                output[(offset + i) * mChannelCount + 1] = r;
            } else {
                output[offset + i] = (l + r) * 0.5f;
            }
            mWaveform[mWaveformIndex] = l;
            mWaveformIndex = (mWaveformIndex + 1) % 256;
        }
        recordSamples(left, right, frames);

        mMidiBuffer.clear();
        mBeatPosition += static_cast<double>(frames) / mSamplesPerBeat;
        mSamplePosition += frames;
        remaining -= frames;
        offset += frames;
        firstChunk = false;
    }
    return oboe::DataCallbackResult::Continue;
}

void YawnEngine::onErrorBeforeClose(oboe::AudioStream* /* stream */,
                                    oboe::Result /* error */) {}

void YawnEngine::onErrorAfterClose(oboe::AudioStream* /* stream */,
                                   oboe::Result /* error */) {
    if (mInitialized.load()) {
        oboeInit();
    }
}

bool YawnEngine::loadWavetable(const std::string& path, std::string* error) {
    std::lock_guard<std::mutex> lock(mWavetableMutex);
    if (!mInstrument) {
        if (error) *error = "No instrument is initialized";
        return false;
    }
    auto* wavetable =
        dynamic_cast<yawn::instruments::WavetableSynth*>(mInstrument.get());
    if (!wavetable) {
        if (error) *error = "The wavetable synth is not selected";
        return false;
    }

    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();
    const bool ok = wavetable->loadCustomWavetable(path, error);
    if (ok) {
        mWavetablePath = path;
        mInstrumentParams.values[0].store(
            static_cast<float>(yawn::instruments::WavetableSynth::kCustomTable));
    }
    if (wasRunning && mStream) mStream->requestStart();
    return ok;
}

bool YawnEngine::saveWavetable(const std::string& path,
                               std::string* error) const {
    std::lock_guard<std::mutex> lock(const_cast<std::mutex&>(mWavetableMutex));
    if (!mInstrument) {
        if (error) *error = "No instrument is initialized";
        return false;
    }
    const auto* wavetable =
        dynamic_cast<const yawn::instruments::WavetableSynth*>(mInstrument.get());
    if (!wavetable) {
        if (error) *error = "The wavetable synth is not selected";
        return false;
    }
    return wavetable->saveCustomWavetable(path, error);
}

void YawnEngine::clearWavetable() {
    {
        std::lock_guard<std::mutex> lock(mWavetableMutex);
        mWavetablePath.clear();
    }
    if (mInstrumentIndex != static_cast<int>(InstrumentKind::Wavetable)) {
        selectInstrument(static_cast<int>(InstrumentKind::Wavetable));
    }
    setInstrumentParam(0, 0.0f);
}

bool YawnEngine::hasCustomWavetable() const {
    std::lock_guard<std::mutex> lock(const_cast<std::mutex&>(mWavetableMutex));
    if (!mInstrument) return false;
    const auto* wavetable =
        dynamic_cast<const yawn::instruments::WavetableSynth*>(mInstrument.get());
    return wavetable && wavetable->hasCustomWavetable();
}

float* YawnEngine::wavetableFrame(float position, float* out,
                                  int length) const {
    if (!out || length <= 0) return out;
    std::fill(out, out + length, 0.0f);
    if (!mInstrument) return out;
    const auto* wavetable =
        dynamic_cast<const yawn::instruments::WavetableSynth*>(mInstrument.get());
    if (!wavetable) return out;

    const int table = wavetable->currentTable();
    const int frames = wavetable->frameCount(table);
    if (frames <= 0) return out;
    const float pos = std::clamp(position, 0.0f, 1.0f) * (frames - 1);
    const int frame0 = std::clamp(static_cast<int>(std::floor(pos)), 0, frames - 1);
    const int frame1 = std::min(frame0 + 1, frames - 1);
    const float mix = pos - frame0;
    const float* a = wavetable->frameData(table, frame0);
    const float* b = wavetable->frameData(table, frame1);
    if (!a || !b) return out;
    const int srcLen = wavetable->frameSize();
    for (int i = 0; i < length; ++i) {
        const float srcPos = length == 1
                                 ? 0.0f
                                 : static_cast<float>(i) *
                                       static_cast<float>(srcLen) /
                                       static_cast<float>(length);
        int i0 = static_cast<int>(srcPos);
        if (i0 >= srcLen) i0 = srcLen - 1;
        const int i1 = (i0 + 1) % srcLen;
        const float frac = srcPos - i0;
        const float sa = a[i0] + (a[i1] - a[i0]) * frac;
        const float sb = b[i0] + (b[i1] - b[i0]) * frac;
        out[i] = sa + (sb - sa) * mix;
    }
    return out;
}
