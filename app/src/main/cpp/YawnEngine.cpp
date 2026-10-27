#include "YawnEngine.h"

#include <algorithm>
#include <chrono>
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
#include "instruments/KarplusStrong.h"
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
#include <nlohmann/json.hpp>

namespace {
constexpr float kCcTimeMax = 5.0f;
constexpr float MicrotonalBendRange = 48.0f;

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
    mMetadataInstruments[2] = std::make_unique<yawn::instruments::KarplusStrong>();

    mMidiCatalog[0] = std::make_unique<yawn::midi::Arpeggiator>();
    mMidiCatalog[1] = std::make_unique<yawn::midi::Chord>();
    mMidiCatalog[2] = std::make_unique<yawn::midi::Scale>();
    mMidiCatalog[3] = std::make_unique<yawn::midi::NoteLength>();
    mMidiCatalog[4] = std::make_unique<yawn::midi::VelocityEffect>();
    mMidiCatalog[5] = std::make_unique<yawn::midi::MidiRandom>();
    mMidiCatalog[6] = std::make_unique<yawn::midi::MidiPitch>();
    mMidiCatalog[7] = std::make_unique<yawn::midi::LFO>();

    mAudioCatalog[0] = std::make_unique<yawn::effects::Delay>();
    mAudioCatalog[1] = std::make_unique<yawn::effects::Chorus>();
    mAudioCatalog[2] = std::make_unique<yawn::effects::Reverb>();
    mAudioCatalog[3] = std::make_unique<yawn::effects::Filter>();
    mAudioCatalog[4] = std::make_unique<yawn::effects::Distortion>();
    mAudioCatalog[5] = std::make_unique<yawn::effects::EQ>();
    mAudioCatalog[6] = std::make_unique<yawn::effects::Compressor>();
    mAudioCatalog[7] = std::make_unique<yawn::effects::Limiter>();

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

    mSafetyLimiterEffect.init(mSampleRate, kMaxFrames);
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
    clearActiveNotes();

    mMixBuffer.assign(static_cast<size_t>(kMaxFrames) * 2, 0.0f);
    mRecordCapacity = mSampleRate * 60;
    mRecordBuffer.assign(static_cast<size_t>(mRecordCapacity) * 2, 0.0f);
    mRecordLength = 0;
    mPlayStart = 0;
    mPlayEnd = 0;
    mPlayIndex = 0;
    mRecording = false;
    mPlayingLoop = false;
    mCrossfadeSamples = mSampleRate / 50; // 20 ms loop seam
    mPlayFadeSamples = mSampleRate / 100; // 10 ms first-start fade
    mPlayFadeRemaining = 0;
    mLoopRecording.store(false);
    mLoopPlaying.store(false);
    mLoopRecordLength.store(0);
    mLoopPlayIndex.store(0);
    mLoopPlayStart.store(0);
    mLoopPlayEnd.store(0);

    mModSmoothCoeff = 1.0f - std::exp(
        -1.0f / (0.02f * static_cast<float>(mSampleRate)));
    for (auto& source : mModSources) {
        source.store(0.0f);
    }
    mModSmoothed.fill(0.0f);
    mLastModTargetCount = 0;

    mInstrumentIndex = std::clamp(instrumentIndex, 0, kInstrumentCount - 1);
    initInstrumentBanks();
    mInstrument = createInstrument(mInstrumentIndex);
    initMidiChain();
    initAudioChain();

    if (mInstrument) {
        mInstrument->init(mSampleRate, kMaxFrames);
        for (int p = 0; p < mInstrument->parameterCount(); ++p) {
            mInstrument->setParameter(
                p, currentInstrumentParams().values[p].load());
        }
    }

    mMidiBuffer.clear();
    mEvents.clear();
    mWaveformIndex = 0;
    std::fill(std::begin(mWaveform), std::end(mWaveform), 0.0f);

    mSuspended.store(false);
    mDspLoad.store(0.0f);
    mDspPeak.store(0.0f);
    mInitialized.store(true);
    oboeInit();
}

void YawnEngine::shutdown() {
    mInitialized.store(false);
    mSuspended.store(false);
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
    if (mStream) {
        mStream.reset();
    }
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
    mSuspended.store(true);
    if (mStream) {
        mStream->stop();
    }
    if (mInstrument) {
        mInstrument->reset();
    }
    resetChainEffects();
    mEvents.clear();
    mMidiBuffer.clear();
    clearActiveNotes();
    mLastModTargetCount = 0;
    mPolyphonyGain = 1.0f;
    mRecording = false;
    mPlayingLoop = false;
    mPlayFadeRemaining = 0;
    mLoopRecording.store(false);
    mLoopPlaying.store(false);
    mLoopPlayIndex.store(0);
    mSafetyLimiterEffect.reset();
}

void YawnEngine::resumeAudio() {
    mSuspended.store(false);
    if (!mInitialized.load()) {
        return;
    }
    if (!mStream) {
        oboeInit();
        return;
    }
    const oboe::StreamState state = mStream->getState();
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
            mStream->requestStart();
            break;
        default:
            break;
    }
}

std::unique_ptr<yawn::instruments::Instrument> YawnEngine::createInstrument(
    int index) const {
    switch (index) {
        case static_cast<int>(InstrumentKind::Karplus):
            return std::make_unique<yawn::instruments::KarplusStrong>();
        case static_cast<int>(InstrumentKind::Subtractive):
            return std::make_unique<yawn::instruments::SubtractiveSynth>();
        case static_cast<int>(InstrumentKind::Wavetable):
        default:
            return std::make_unique<yawn::instruments::WavetableSynth>();
    }
}

YawnEngine::ParamState& YawnEngine::currentInstrumentParams() {
    return mInstrumentParamsByType[std::clamp(mInstrumentIndex, 0,
                                               kInstrumentCount - 1)];
}

const YawnEngine::ParamState& YawnEngine::currentInstrumentParams() const {
    return mInstrumentParamsByType[std::clamp(mInstrumentIndex, 0,
                                               kInstrumentCount - 1)];
}

void YawnEngine::initInstrumentBanks() {
    for (int inst = 0; inst < kInstrumentCount; ++inst) {
        const auto& metadata = *mMetadataInstruments[inst];
        const int count = std::min(metadata.parameterCount(), kMaxParams);
        for (int p = 0; p < count; ++p) {
            mInstrumentParamsByType[inst].values[p].store(
                metadata.parameterInfo(p).defaultValue);
        }
    }
}

std::unique_ptr<yawn::midi::MidiEffect> YawnEngine::createMidiEffect(
    int type) const {
    switch (type) {
        case 0: return std::make_unique<yawn::midi::Arpeggiator>();
        case 1: return std::make_unique<yawn::midi::Chord>();
        case 2: return std::make_unique<yawn::midi::Scale>();
        case 3: return std::make_unique<yawn::midi::NoteLength>();
        case 4: return std::make_unique<yawn::midi::VelocityEffect>();
        case 5: return std::make_unique<yawn::midi::MidiRandom>();
        case 6: return std::make_unique<yawn::midi::MidiPitch>();
        case 7: return std::make_unique<yawn::midi::LFO>();
        default: return nullptr;
    }
}

std::unique_ptr<yawn::effects::AudioEffect> YawnEngine::createAudioEffect(
    int type) const {
    switch (type) {
        case 0: return std::make_unique<yawn::effects::Delay>();
        case 1: return std::make_unique<yawn::effects::Chorus>();
        case 2: return std::make_unique<yawn::effects::Reverb>();
        case 3: return std::make_unique<yawn::effects::Filter>();
        case 4: return std::make_unique<yawn::effects::Distortion>();
        case 5: return std::make_unique<yawn::effects::EQ>();
        case 6: return std::make_unique<yawn::effects::Compressor>();
        case 7: return std::make_unique<yawn::effects::Limiter>();
        default: return nullptr;
    }
}

void YawnEngine::clearMidiChain() {
    for (auto& slot : mMidiChain) slot.reset();
    mMidiChainCount = 0;
}

void YawnEngine::clearAudioChain() {
    for (auto& slot : mAudioChain) slot.reset();
    mAudioChainCount = 0;
}

int YawnEngine::appendMidiEffect(int type, bool bypassed) {
    if (mMidiChainCount >= kMaxMidiChainSlots) return -1;
    auto effect = createMidiEffect(type);
    if (!effect) return -1;
    auto slot = std::make_unique<MidiChainSlot>();
    slot->type = type;
    slot->bypassed.store(bypassed);
    slot->effect = std::move(effect);
    slot->effect->init(mSampleRate);
    slot->effect->setBypassed(bypassed);
    const int count = std::min(slot->effect->parameterCount(), kMaxParams);
    for (int p = 0; p < count; ++p) {
        const float value = slot->effect->parameterInfo(p).defaultValue;
        slot->params.values[p].store(value);
        slot->effect->setParameter(p, value);
    }
    mMidiChain[mMidiChainCount] = std::move(slot);
    return mMidiChainCount++;
}

int YawnEngine::appendAudioEffect(int type, bool bypassed) {
    if (mAudioChainCount >= kMaxAudioChainSlots) return -1;
    auto effect = createAudioEffect(type);
    if (!effect) return -1;
    auto slot = std::make_unique<AudioChainSlot>();
    slot->type = type;
    slot->bypassed.store(bypassed);
    slot->effect = std::move(effect);
    slot->effect->init(mSampleRate, kMaxFrames);
    slot->effect->setBypassed(bypassed);
    const int count = std::min(slot->effect->parameterCount(), kMaxParams);
    for (int p = 0; p < count; ++p) {
        const float value = slot->effect->parameterInfo(p).defaultValue;
        slot->params.values[p].store(value);
        slot->effect->setParameter(p, value);
    }
    mAudioChain[mAudioChainCount] = std::move(slot);
    return mAudioChainCount++;
}

void YawnEngine::initMidiChain() {
    clearMidiChain();
    appendMidiEffect(0, true); // Arpeggiator
    appendMidiEffect(1, true); // Chord
    appendMidiEffect(2, true); // Scale
}

void YawnEngine::initAudioChain() {
    clearAudioChain();
    appendAudioEffect(0, true); // Delay
    appendAudioEffect(1, true); // Chorus
    appendAudioEffect(2, true); // Reverb
    appendAudioEffect(7, true); // Limiter
}

void YawnEngine::resetChainEffects() {
    for (int i = 0; i < mMidiChainCount; ++i) {
        if (mMidiChain[i] && mMidiChain[i]->effect) mMidiChain[i]->effect->reset();
    }
    for (int i = 0; i < mAudioChainCount; ++i) {
        if (mAudioChain[i] && mAudioChain[i]->effect) mAudioChain[i]->effect->reset();
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
    if (mInstrument) {
        mInstrument->init(mSampleRate, kMaxFrames);
        for (int p = 0; p < mInstrument->parameterCount(); ++p) {
            mInstrument->setParameter(
                p, currentInstrumentParams().values[p].load());
        }
        auto* wavetable =
            dynamic_cast<yawn::instruments::WavetableSynth*>(mInstrument.get());
        if (wavetable && !mWavetablePath.empty()) {
            std::string error;
            wavetable->loadCustomWavetable(mWavetablePath, &error);
            currentInstrumentParams().values[0].store(
                static_cast<float>(yawn::instruments::WavetableSynth::kCustomTable));
        }
    }
    resetChainEffects();
    mEvents.clear();
    mMidiBuffer.clear();
    mLastModTargetCount = 0;

    if (wasRunning && mStream) mStream->requestStart();
    return true;
}

const char* YawnEngine::instrumentName(int index) {
    static const char* kNames[] = {"YAWN Wavetable", "YAWN Subtractive",
                                   "YAWN Karplus-Strong"};
    if (index < 0 || index >= kInstrumentCount) return "";
    return kNames[index];
}

const char* YawnEngine::instrumentId(int index) {
    static const char* kIds[] = {"wavetable", "subsynth", "karplus"};
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
    return currentInstrumentParams().values[index].load();
}

void YawnEngine::setInstrumentParam(int index, float value) {
    const auto* info = instrumentInfo(index);
    if (!info) return;
    const float clamped = clampParam(*info, value);
    currentInstrumentParams().values[index].store(clamped);
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
    if (effect < 0 || effect >= kMidiEffectCount || !mMidiCatalog[effect])
        return nullptr;
    if (index < 0 || index >= mMidiCatalog[effect]->parameterCount())
        return nullptr;
    return &mMidiCatalog[effect]->parameterInfo(index);
}

const yawn::ParameterInfo* YawnEngine::midiChainInfo(int slot, int index) const {
    if (slot < 0 || slot >= mMidiChainCount || !mMidiChain[slot] ||
        !mMidiChain[slot]->effect)
        return nullptr;
    if (index < 0 || index >= mMidiChain[slot]->effect->parameterCount())
        return nullptr;
    return &mMidiChain[slot]->effect->parameterInfo(index);
}

int YawnEngine::midiEffectParamCount(int effect) const {
    if (effect < 0 || effect >= kMidiEffectCount || !mMidiCatalog[effect])
        return 0;
    return mMidiCatalog[effect]->parameterCount();
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
    const auto* info = midiInfo(effect, index);
    return info ? info->defaultValue : 0.0f;
}

void YawnEngine::setMidiEffectParam(int /* effect */, int /* index */,
                                    float /* value */) {
}

bool YawnEngine::midiEffectBypassed(int /* index */) const {
    return true;
}

void YawnEngine::setMidiEffectBypassed(int /* index */, bool /* bypassed */) {
}

const char* YawnEngine::midiChainName(int slot) const {
    if (slot < 0 || slot >= mMidiChainCount || !mMidiChain[slot]) return "";
    return midiEffectName(mMidiChain[slot]->type);
}

const char* YawnEngine::midiChainId(int slot) const {
    if (slot < 0 || slot >= mMidiChainCount || !mMidiChain[slot]) return "";
    return midiEffectId(mMidiChain[slot]->type);
}

bool YawnEngine::midiChainBypassed(int slot) const {
    if (slot < 0 || slot >= mMidiChainCount || !mMidiChain[slot]) return true;
    return mMidiChain[slot]->bypassed.load();
}

void YawnEngine::setMidiChainBypassed(int slot, bool bypassed) {
    if (slot < 0 || slot >= mMidiChainCount || !mMidiChain[slot]) return;
    mMidiChain[slot]->bypassed.store(bypassed);
    EngineEvent event;
    event.type = EngineEvent::Type::SetMidiEffectBypass;
    event.i = slot;
    event.a = bypassed ? 1 : 0;
    push(event);
}

int YawnEngine::addMidiEffect(int type) {
    if (type < 0 || type >= kMidiEffectCount) return -1;
    std::lock_guard<std::mutex> lock(mControlMutex);
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();
    const int slot = appendMidiEffect(type, false);
    mEvents.clear();
    mMidiBuffer.clear();
    if (wasRunning && mStream) mStream->requestStart();
    return slot;
}

bool YawnEngine::removeMidiEffect(int slot) {
    if (slot < 0 || slot >= mMidiChainCount) return false;
    std::lock_guard<std::mutex> lock(mControlMutex);
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();
    for (int i = slot; i < mMidiChainCount - 1; ++i) {
        mMidiChain[i] = std::move(mMidiChain[i + 1]);
    }
    mMidiChain[mMidiChainCount - 1].reset();
    --mMidiChainCount;
    mEvents.clear();
    mMidiBuffer.clear();
    if (wasRunning && mStream) mStream->requestStart();
    return true;
}

bool YawnEngine::moveMidiEffect(int slot, int delta) {
    const int target = slot + delta;
    if (slot < 0 || slot >= mMidiChainCount || target < 0 ||
        target >= mMidiChainCount)
        return false;
    std::lock_guard<std::mutex> lock(mControlMutex);
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();
    std::swap(mMidiChain[slot], mMidiChain[target]);
    mEvents.clear();
    mMidiBuffer.clear();
    if (wasRunning && mStream) mStream->requestStart();
    return true;
}

int YawnEngine::midiChainParamCount(int slot) const {
    const auto& s = mMidiChain;
    if (slot < 0 || slot >= mMidiChainCount || !s[slot] || !s[slot]->effect)
        return 0;
    return s[slot]->effect->parameterCount();
}

const char* YawnEngine::midiChainParamName(int slot, int index) const {
    const auto* info = midiChainInfo(slot, index);
    return info ? info->name : "";
}

float YawnEngine::midiChainParamMin(int slot, int index) const {
    const auto* info = midiChainInfo(slot, index);
    return info ? info->minValue : 0.0f;
}

float YawnEngine::midiChainParamMax(int slot, int index) const {
    const auto* info = midiChainInfo(slot, index);
    return info ? info->maxValue : 0.0f;
}

float YawnEngine::midiChainParamDefault(int slot, int index) const {
    const auto* info = midiChainInfo(slot, index);
    return info ? info->defaultValue : 0.0f;
}

bool YawnEngine::midiChainParamIsBoolean(int slot, int index) const {
    const auto* info = midiChainInfo(slot, index);
    return info && info->isBoolean;
}

int YawnEngine::midiChainParamLabelCount(int slot, int index) const {
    const auto* info = midiChainInfo(slot, index);
    return info ? info->valueLabelCount : 0;
}

const char* YawnEngine::midiChainParamLabel(int slot, int index,
                                            int label) const {
    const auto* info = midiChainInfo(slot, index);
    if (!info || !info->valueLabels || label < 0 ||
        label >= info->valueLabelCount)
        return "";
    return info->valueLabels[label];
}

float YawnEngine::midiChainParamValue(int slot, int index) const {
    if (slot < 0 || slot >= mMidiChainCount || !mMidiChain[slot] ||
        index < 0 || index >= kMaxParams)
        return 0.0f;
    return mMidiChain[slot]->params.values[index].load();
}

void YawnEngine::setMidiChainParam(int slot, int index, float value) {
    const auto* info = midiChainInfo(slot, index);
    if (!info || slot < 0 || slot >= mMidiChainCount) return;
    const float clamped = clampParam(*info, value);
    mMidiChain[slot]->params.values[index].store(clamped);
    EngineEvent event;
    event.type = EngineEvent::Type::SetMidiEffectParam;
    event.i = slot;
    event.j = index;
    event.f = clamped;
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
    if (effect < 0 || effect >= kAudioEffectCount || !mAudioCatalog[effect])
        return nullptr;
    if (index < 0 || index >= mAudioCatalog[effect]->parameterCount())
        return nullptr;
    return &mAudioCatalog[effect]->parameterInfo(index);
}

const yawn::ParameterInfo* YawnEngine::audioChainInfo(int slot, int index) const {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot] ||
        !mAudioChain[slot]->effect)
        return nullptr;
    if (index < 0 || index >= mAudioChain[slot]->effect->parameterCount())
        return nullptr;
    return &mAudioChain[slot]->effect->parameterInfo(index);
}

int YawnEngine::audioEffectParamCount(int effect) const {
    if (effect < 0 || effect >= kAudioEffectCount || !mAudioCatalog[effect])
        return 0;
    return mAudioCatalog[effect]->parameterCount();
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
    const auto* info = audioInfo(effect, index);
    return info ? info->defaultValue : 0.0f;
}

void YawnEngine::setAudioEffectParam(int /* effect */, int /* index */,
                                     float /* value */) {
}

bool YawnEngine::audioEffectBypassed(int /* index */) const {
    return true;
}

void YawnEngine::setAudioEffectBypassed(int /* index */, bool /* bypassed */) {
}

const char* YawnEngine::audioChainName(int slot) const {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot]) return "";
    return audioEffectName(mAudioChain[slot]->type);
}

const char* YawnEngine::audioChainId(int slot) const {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot]) return "";
    return audioEffectId(mAudioChain[slot]->type);
}

bool YawnEngine::audioChainBypassed(int slot) const {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot]) return true;
    return mAudioChain[slot]->bypassed.load();
}

void YawnEngine::setAudioChainBypassed(int slot, bool bypassed) {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot]) return;
    mAudioChain[slot]->bypassed.store(bypassed);
    EngineEvent event;
    event.type = EngineEvent::Type::SetAudioEffectBypass;
    event.i = slot;
    event.a = bypassed ? 1 : 0;
    push(event);
}

int YawnEngine::addAudioEffect(int type) {
    if (type < 0 || type >= kAudioEffectCount) return -1;
    std::lock_guard<std::mutex> lock(mControlMutex);
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();
    const int slot = appendAudioEffect(type, false);
    mEvents.clear();
    mMidiBuffer.clear();
    if (wasRunning && mStream) mStream->requestStart();
    return slot;
}

bool YawnEngine::removeAudioEffect(int slot) {
    if (slot < 0 || slot >= mAudioChainCount) return false;
    std::lock_guard<std::mutex> lock(mControlMutex);
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();
    for (int i = slot; i < mAudioChainCount - 1; ++i) {
        mAudioChain[i] = std::move(mAudioChain[i + 1]);
    }
    mAudioChain[mAudioChainCount - 1].reset();
    --mAudioChainCount;
    mEvents.clear();
    mMidiBuffer.clear();
    if (wasRunning && mStream) mStream->requestStart();
    return true;
}

bool YawnEngine::moveAudioEffect(int slot, int delta) {
    const int target = slot + delta;
    if (slot < 0 || slot >= mAudioChainCount || target < 0 ||
        target >= mAudioChainCount)
        return false;
    std::lock_guard<std::mutex> lock(mControlMutex);
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();
    std::swap(mAudioChain[slot], mAudioChain[target]);
    mEvents.clear();
    mMidiBuffer.clear();
    if (wasRunning && mStream) mStream->requestStart();
    return true;
}

int YawnEngine::audioChainParamCount(int slot) const {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot] ||
        !mAudioChain[slot]->effect)
        return 0;
    return mAudioChain[slot]->effect->parameterCount();
}

const char* YawnEngine::audioChainParamName(int slot, int index) const {
    const auto* info = audioChainInfo(slot, index);
    return info ? info->name : "";
}

float YawnEngine::audioChainParamMin(int slot, int index) const {
    const auto* info = audioChainInfo(slot, index);
    return info ? info->minValue : 0.0f;
}

float YawnEngine::audioChainParamMax(int slot, int index) const {
    const auto* info = audioChainInfo(slot, index);
    return info ? info->maxValue : 0.0f;
}

float YawnEngine::audioChainParamDefault(int slot, int index) const {
    const auto* info = audioChainInfo(slot, index);
    return info ? info->defaultValue : 0.0f;
}

bool YawnEngine::audioChainParamIsBoolean(int slot, int index) const {
    const auto* info = audioChainInfo(slot, index);
    return info && info->isBoolean;
}

int YawnEngine::audioChainParamLabelCount(int slot, int index) const {
    const auto* info = audioChainInfo(slot, index);
    return info ? info->valueLabelCount : 0;
}

const char* YawnEngine::audioChainParamLabel(int slot, int index,
                                             int label) const {
    const auto* info = audioChainInfo(slot, index);
    if (!info || !info->valueLabels || label < 0 ||
        label >= info->valueLabelCount)
        return "";
    return info->valueLabels[label];
}

float YawnEngine::audioChainParamValue(int slot, int index) const {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot] ||
        index < 0 || index >= kMaxParams)
        return 0.0f;
    return mAudioChain[slot]->params.values[index].load();
}

void YawnEngine::setAudioChainParam(int slot, int index, float value) {
    const auto* info = audioChainInfo(slot, index);
    if (!info || slot < 0 || slot >= mAudioChainCount) return;
    const float clamped = clampParam(*info, value);
    mAudioChain[slot]->params.values[index].store(clamped);
    EngineEvent event;
    event.type = EngineEvent::Type::SetAudioEffectParam;
    event.i = slot;
    event.j = index;
    event.f = clamped;
    push(event);
}

std::string YawnEngine::saveState() const {
    nlohmann::json root;
    root["version"] = 1;
    root["tempo"] = mTransport.bpm;
    root["masterVolume"] = mMasterVolume;

    nlohmann::json instruments = nlohmann::json::array();
    for (int inst = 0; inst < kInstrumentCount; ++inst) {
        const auto& metadata = *mMetadataInstruments[inst];
        nlohmann::json entry;
        entry["type"] = instrumentId(inst);
        nlohmann::json params = nlohmann::json::array();
        const int count = std::min(metadata.parameterCount(), kMaxParams);
        for (int p = 0; p < count; ++p) {
            params.push_back(
                mInstrumentParamsByType[inst].values[p].load());
        }
        entry["params"] = std::move(params);
        instruments.push_back(std::move(entry));
    }
    root["instruments"] = std::move(instruments);

    nlohmann::json midiChain = nlohmann::json::array();
    for (int i = 0; i < mMidiChainCount; ++i) {
        if (!mMidiChain[i] || !mMidiChain[i]->effect) continue;
        nlohmann::json entry;
        entry["type"] = midiEffectId(mMidiChain[i]->type);
        entry["bypass"] = mMidiChain[i]->bypassed.load();
        nlohmann::json params = nlohmann::json::array();
        const int count =
            std::min(mMidiChain[i]->effect->parameterCount(), kMaxParams);
        for (int p = 0; p < count; ++p) {
            params.push_back(mMidiChain[i]->params.values[p].load());
        }
        entry["params"] = std::move(params);
        midiChain.push_back(std::move(entry));
    }
    root["midiChain"] = std::move(midiChain);

    nlohmann::json audioChain = nlohmann::json::array();
    for (int i = 0; i < mAudioChainCount; ++i) {
        if (!mAudioChain[i] || !mAudioChain[i]->effect) continue;
        nlohmann::json entry;
        entry["type"] = audioEffectId(mAudioChain[i]->type);
        entry["bypass"] = mAudioChain[i]->bypassed.load();
        nlohmann::json params = nlohmann::json::array();
        const int count =
            std::min(mAudioChain[i]->effect->parameterCount(), kMaxParams);
        for (int p = 0; p < count; ++p) {
            params.push_back(mAudioChain[i]->params.values[p].load());
        }
        entry["params"] = std::move(params);
        audioChain.push_back(std::move(entry));
    }
    root["audioChain"] = std::move(audioChain);

    return root.dump();
}

bool YawnEngine::restoreState(const std::string& jsonText,
                              std::string* error) {
    if (error) error->clear();
    if (jsonText.empty()) return true;

    nlohmann::json root;
    try {
        root = nlohmann::json::parse(jsonText);
    } catch (const std::exception& e) {
        if (error) *error = std::string("invalid state JSON: ") + e.what();
        return false;
    }
    if (!root.is_object() || root.value("version", 0) != 1) {
        if (error) *error = "unsupported synth state version";
        return false;
    }

    struct ChainSpec {
        int type = -1;
        bool bypass = true;
        std::vector<float> params;
    };
    auto parseChain = [&](const nlohmann::json& array, bool midi,
                          std::vector<ChainSpec>& out) {
        if (!array.is_array()) return false;
        const int typeCount = midi ? kMidiEffectCount : kAudioEffectCount;
        const int maxSlots = midi ? kMaxMidiChainSlots : kMaxAudioChainSlots;
        for (const auto& entry : array) {
            const std::string id = entry.value("type", std::string{});
            int type = -1;
            for (int t = 0; t < typeCount; ++t) {
                const char* candidate =
                    midi ? midiEffectId(t) : audioEffectId(t);
                if (id == candidate) {
                    type = t;
                    break;
                }
            }
            if (type < 0) continue;
            if (static_cast<int>(out.size()) >= maxSlots) {
                return false;
            }
            ChainSpec spec;
            spec.type = type;
            spec.bypass = entry.value("bypass", true);
            if (entry.contains("params") && entry["params"].is_array()) {
                for (const auto& value : entry["params"]) {
                    if (value.is_number()) {
                        spec.params.push_back(value.get<float>());
                    }
                }
            }
            out.push_back(std::move(spec));
        }
        return true;
    };

    std::vector<ChainSpec> midiChain;
    std::vector<ChainSpec> audioChain;
    if (root.contains("midiChain") &&
        !parseChain(root["midiChain"], true, midiChain)) {
        if (error) *error = "invalid MIDI effect chain in state";
        return false;
    }
    if (root.contains("audioChain") &&
        !parseChain(root["audioChain"], false, audioChain)) {
        if (error) *error = "invalid audio effect chain in state";
        return false;
    }

    std::array<std::vector<float>, kInstrumentCount> banks;
    if (root.contains("instruments") && root["instruments"].is_array()) {
        for (const auto& entry : root["instruments"]) {
            const std::string id = entry.value("type", std::string{});
            int inst = -1;
            for (int i = 0; i < kInstrumentCount; ++i) {
                if (id == instrumentId(i)) {
                    inst = i;
                    break;
                }
            }
            if (inst < 0) continue;
            if (entry.contains("params") && entry["params"].is_array()) {
                for (const auto& value : entry["params"]) {
                    if (value.is_number()) {
                        banks[inst].push_back(value.get<float>());
                    }
                }
            }
        }
    }

    std::lock_guard<std::mutex> lock(mControlMutex);
    const bool wasRunning = audioIsRunning();
    if (mStream) mStream->stop();

    if (root.contains("instruments")) {
        for (int inst = 0; inst < kInstrumentCount; ++inst) {
            const auto& metadata = *mMetadataInstruments[inst];
            const int count = std::min(metadata.parameterCount(), kMaxParams);
            for (int p = 0; p < count; ++p) {
                const auto& info = metadata.parameterInfo(p);
                const float value = p < static_cast<int>(banks[inst].size())
                                        ? banks[inst][p]
                                        : info.defaultValue;
                mInstrumentParamsByType[inst].values[p].store(
                    clampParam(info, value));
            }
        }
        if (mInstrument) {
            const int count =
                std::min(mInstrument->parameterCount(), kMaxParams);
            for (int p = 0; p < count; ++p) {
                mInstrument->setParameter(
                    p, currentInstrumentParams().values[p].load());
            }
        }
    }

    if (root.contains("tempo") && root["tempo"].is_number()) {
        mTransport.bpm = std::clamp(root["tempo"].get<float>(), 20.0f, 300.0f);
        mSamplesPerBeat =
            static_cast<double>(mSampleRate) * 60.0 / mTransport.bpm;
        mTransport.samplesPerBeat = mSamplesPerBeat;
    }
    if (root.contains("masterVolume") && root["masterVolume"].is_number()) {
        mMasterVolume =
            std::clamp(root["masterVolume"].get<float>(), 0.0f, 2.0f);
    }

    if (root.contains("midiChain")) {
        clearMidiChain();
        for (const auto& spec : midiChain) {
            const int slot = appendMidiEffect(spec.type, spec.bypass);
            if (slot < 0) continue;
            auto& chainSlot = mMidiChain[slot];
            const int count =
                std::min(chainSlot->effect->parameterCount(), kMaxParams);
            for (int p = 0; p < count; ++p) {
                const auto& info = chainSlot->effect->parameterInfo(p);
                const float value = p < static_cast<int>(spec.params.size())
                                        ? spec.params[p]
                                        : info.defaultValue;
                const float clamped = clampParam(info, value);
                chainSlot->params.values[p].store(clamped);
                chainSlot->effect->setParameter(p, clamped);
            }
            chainSlot->bypassed.store(spec.bypass);
            chainSlot->effect->setBypassed(spec.bypass);
        }
    }
    if (root.contains("audioChain")) {
        clearAudioChain();
        for (const auto& spec : audioChain) {
            const int slot = appendAudioEffect(spec.type, spec.bypass);
            if (slot < 0) continue;
            auto& chainSlot = mAudioChain[slot];
            const int count =
                std::min(chainSlot->effect->parameterCount(), kMaxParams);
            for (int p = 0; p < count; ++p) {
                const auto& info = chainSlot->effect->parameterInfo(p);
                const float value = p < static_cast<int>(spec.params.size())
                                        ? spec.params[p]
                                        : info.defaultValue;
                const float clamped = clampParam(info, value);
                chainSlot->params.values[p].store(clamped);
                chainSlot->effect->setParameter(p, clamped);
            }
            chainSlot->bypassed.store(spec.bypass);
            chainSlot->effect->setBypassed(spec.bypass);
        }
    }

    mEvents.clear();
    mMidiBuffer.clear();
    if (wasRunning && mStream) mStream->requestStart();
    return true;
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

void YawnEngine::notePitch(int channel, int note, float bendSemitones) {
    EngineEvent event;
    event.type = EngineEvent::Type::NotePitch;
    event.channel = static_cast<uint8_t>(channel);
    event.a = static_cast<uint8_t>(note);
    event.f = bendSemitones;
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
    } else if (mInstrumentIndex ==
               static_cast<int>(InstrumentKind::Subtractive)) {
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
    } else if (mInstrumentIndex ==
               static_cast<int>(InstrumentKind::Subtractive)) {
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

void YawnEngine::setSafetyLimiter(bool enabled) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetSafetyLimiter;
    event.a = enabled ? 1 : 0;
    push(event);
}

void YawnEngine::setPolyphonyLeveling(bool enabled) {
    EngineEvent event;
    event.type = EngineEvent::Type::SetPolyphonyLeveling;
    event.a = enabled ? 1 : 0;
    push(event);
}

void YawnEngine::setModSource(int source, float value) {
    if (source < 0 || source >= kModSourceCount) {
        return;
    }
    mModSources[source].store(std::clamp(value, -1.0f, 1.0f),
                              std::memory_order_relaxed);
}

void YawnEngine::setModSlot(int slot, bool enabled, int source,
                            int targetType, int targetSlot,
                            int targetParam, float amount) {
    if (slot < 0 || slot >= kMaxModSlots) {
        return;
    }
    EngineEvent event;
    event.type = EngineEvent::Type::SetModSlot;
    event.i = slot;
    event.j = enabled ? 1 : 0;
    event.a = static_cast<uint8_t>(std::max(0, std::min(255, source)));
    event.b = static_cast<uint8_t>(std::max(0, std::min(255, targetType)));
    event.c = static_cast<uint8_t>(std::max(0, std::min(255, targetSlot)));
    event.d = static_cast<uint8_t>(std::max(0, std::min(255, targetParam)));
    event.f = std::clamp(amount, -1.0f, 1.0f);
    push(event);
}

void YawnEngine::clearLoop() {
    EngineEvent event;
    event.type = EngineEvent::Type::ClearLoop;
    push(event);
}

float YawnEngine::loopRecordProgress() const {
    if (mRecordCapacity <= 0) {
        return 0.0f;
    }
    const float progress = static_cast<float>(mLoopRecordLength.load()) /
                           static_cast<float>(mRecordCapacity);
    return std::clamp(progress, 0.0f, 1.0f);
}

float YawnEngine::loopPlayProgress() const {
    const int start = mLoopPlayStart.load();
    const int end = mLoopPlayEnd.load();
    if (end <= start) {
        return 0.0f;
    }
    const int index = mLoopPlayIndex.load();
    const float progress = static_cast<float>(index - start) /
                           static_cast<float>(end - start);
    return std::clamp(progress, 0.0f, 1.0f);
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

int YawnEngine::findAudioChainSlot(int type) const {
    for (int i = 0; i < mAudioChainCount; ++i) {
        if (mAudioChain[i] && mAudioChain[i]->type == type) return i;
    }
    return -1;
}

void YawnEngine::applyAudioChainParam(int slot, int index, float value) {
    if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot] ||
        !mAudioChain[slot]->effect || index < 0 || index >= kMaxParams) {
        return;
    }
    const auto* info = audioChainInfo(slot, index);
    if (!info) return;
    const float clamped = clampParam(*info, value);
    mAudioChain[slot]->params.values[index].store(clamped);
    mAudioChain[slot]->effect->setParameter(index, clamped);
}

void YawnEngine::dispatchEvent(const EngineEvent& event, int numFrames) {
    switch (event.type) {
        case EngineEvent::Type::NoteOn:
            handleNoteOn(event.channel, event.a, event.b);
            break;
        case EngineEvent::Type::NoteOff:
            handleNoteOff(event.channel, event.a);
            break;
        case EngineEvent::Type::NotePitch: {
            const int channel = event.channel & 15;
            const int note = event.a & 127;
            mPitchModulation.setBaseBend(channel, note, event.f);
            sendPerNotePitch(channel, note,
                             event.f + mPitchModulation.offsetSemitones());
            break;
        }
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
                } else if (mInstrumentIndex ==
                           static_cast<int>(InstrumentKind::Subtractive)) {
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
            if (mInstrumentIndex ==
                    static_cast<int>(InstrumentKind::Wavetable)) {
                const int base =
                    yawn::instruments::WavetableSynth::kAmpAttack;
                setInstrumentParam(base + 0, ccToSeconds(event.a));
                setInstrumentParam(base + 1, ccToSeconds(event.b));
                setInstrumentParam(base + 2, ccToUnit(event.c));
                setInstrumentParam(base + 3, ccToSeconds(event.d));
            } else if (mInstrumentIndex ==
                       static_cast<int>(InstrumentKind::Subtractive)) {
                const int base =
                    yawn::instruments::SubtractiveSynth::kAmpAttack;
                setInstrumentParam(base + 0, ccToSeconds(event.a));
                setInstrumentParam(base + 1, ccToSeconds(event.b));
                setInstrumentParam(base + 2, ccToUnit(event.c));
                setInstrumentParam(base + 3, ccToSeconds(event.d));
            }
            break;
        }
        case EngineEvent::Type::SetInstrumentParam:
            if (mInstrument) mInstrument->setParameter(event.i, event.f);
            break;
        case EngineEvent::Type::SetMidiEffectParam:
            if (event.i >= 0 && event.i < mMidiChainCount &&
                mMidiChain[event.i] && mMidiChain[event.i]->effect) {
                mMidiChain[event.i]->effect->setParameter(event.j, event.f);
            }
            break;
        case EngineEvent::Type::SetMidiEffectBypass:
            if (event.i >= 0 && event.i < mMidiChainCount &&
                mMidiChain[event.i] && mMidiChain[event.i]->effect) {
                mMidiChain[event.i]->effect->setBypassed(event.a != 0);
            }
            break;
        case EngineEvent::Type::SetAudioEffectParam:
            if (event.i >= 0 && event.i < mAudioChainCount &&
                mAudioChain[event.i] && mAudioChain[event.i]->effect) {
                mAudioChain[event.i]->effect->setParameter(event.j, event.f);
            }
            break;
        case EngineEvent::Type::SetAudioEffectBypass:
            if (event.i >= 0 && event.i < mAudioChainCount &&
                mAudioChain[event.i] && mAudioChain[event.i]->effect) {
                mAudioChain[event.i]->effect->setBypassed(event.a != 0);
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
        case EngineEvent::Type::ClearLoop:
            mRecording = false;
            mPlayingLoop = false;
            mRecordLength = 0;
            mPlayIndex = 0;
            mPlayStart = 0;
            mPlayEnd = 0;
            mPlayFadeRemaining = 0;
            mLoopRecording.store(false);
            mLoopPlaying.store(false);
            mLoopRecordLength.store(0);
            mLoopPlayIndex.store(0);
            mLoopPlayStart.store(0);
            mLoopPlayEnd.store(0);
            break;
        case EngineEvent::Type::SetModSlot:
            if (event.i >= 0 && event.i < kMaxModSlots) {
                ModSlot& modSlot = mModSlots[event.i];
                modSlot.enabled = event.j != 0;
                modSlot.source = event.a;
                modSlot.targetType = event.b;
                modSlot.targetSlot = event.c;
                modSlot.targetParam = event.d;
                modSlot.amount = std::clamp(event.f, -1.0f, 1.0f);
            }
            break;
        case EngineEvent::Type::SetDelayLevel: {
            const int slot = findAudioChainSlot(0);
            if (slot >= 0) {
                applyAudioChainParam(slot, yawn::effects::Delay::kWetDry,
                                     event.f);
                const bool bypassed = event.f <= 0.001f;
                mAudioChain[slot]->bypassed.store(bypassed);
                mAudioChain[slot]->effect->setBypassed(bypassed);
            }
            break;
        }
        case EngineEvent::Type::SetDelayLength: {
            const int slot = findAudioChainSlot(0);
            if (slot >= 0) {
                const float ms = mSampleRate > 0
                                     ? 1000.0f * static_cast<float>(event.i) /
                                           static_cast<float>(mSampleRate)
                                     : 500.0f;
                applyAudioChainParam(slot, yawn::effects::Delay::kTimeMs,
                                     std::clamp(ms, 1.0f, 4000.0f));
                applyAudioChainParam(slot, yawn::effects::Delay::kSync, 0.0f);
            }
            break;
        }
        case EngineEvent::Type::SetDelayFeedback: {
            const int slot = findAudioChainSlot(0);
            if (slot >= 0) {
                applyAudioChainParam(slot, yawn::effects::Delay::kFeedback,
                                     event.f);
            }
            break;
        }
        default:
            break;
    }
}

void YawnEngine::handleNoteOn(int channel, int note, int velocity) {
    mPitchModulation.noteOn(channel & 15, note & 127);
    mMidiBuffer.addMessage(
        yawn::midi::MidiMessage::noteOn(channel, note, velocity));
}

void YawnEngine::handleNoteOff(int channel, int note) {
    mPitchModulation.noteOff(channel & 15, note & 127);
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
    const bool isSubtractive =
        mInstrumentIndex == static_cast<int>(InstrumentKind::Subtractive);
    switch (control) {
        case 16:
            if (isWavetable) {
                setInstrumentParam(yawn::instruments::WavetableSynth::kPosition,
                                   static_cast<float>(value) / 255.0f);
            } else if (isSubtractive) {
                setInstrumentParam(
                    yawn::instruments::SubtractiveSynth::kOsc1Wave,
                    std::round((static_cast<float>(value) / 255.0f) * 4.0f));
            }
            break;
        case 17:
            if (isSubtractive) {
                setInstrumentParam(
                    yawn::instruments::SubtractiveSynth::kOsc2Wave,
                    std::round((static_cast<float>(value) / 255.0f) * 4.0f));
            }
            break;
        case 18:
        case 19:
        case 20:
        case 21: {
            if (!isWavetable && !isSubtractive) break;
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
            if (!isWavetable && !isSubtractive) break;
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
            } else if (isSubtractive) {
                setInstrumentParam(yawn::instruments::SubtractiveSynth::kLFODepth,
                                   ccToUnit(value));
            }
            break;
        case 74:
            if (isWavetable) {
                setInstrumentParam(yawn::instruments::WavetableSynth::kPosition,
                                   ccToUnit(value));
            } else if (isSubtractive) {
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
    clearActiveNotes();
}

void YawnEngine::doSetPlay(bool play) {
    if (play && mRecordLength > 0) {
        mPlayStart = 0;
        mPlayEnd = mRecordLength;
        mPlayIndex = mPlayStart;
        mPlayingLoop = true;
        mPlayFadeRemaining = mPlayFadeSamples;
        mLoopPlaying.store(true);
        mLoopPlayStart.store(mPlayStart);
        mLoopPlayEnd.store(mPlayEnd);
        mLoopPlayIndex.store(mPlayIndex);
    } else {
        mPlayingLoop = false;
        mPlayFadeRemaining = 0;
        mLoopPlaying.store(false);
    }
}

void YawnEngine::doSetRecord(bool record) {
    if (record) {
        mRecordLength = 0;
        mPlayIndex = 0;
        mPlayingLoop = false;
        mRecording = true;
        mPlayFadeRemaining = 0;
        mLoopRecording.store(true);
        mLoopPlaying.store(false);
        mLoopRecordLength.store(0);
        mLoopPlayIndex.store(0);
        mLoopPlayStart.store(0);
        mLoopPlayEnd.store(0);
    } else {
        mRecording = false;
        mLoopRecording.store(false);
        mLoopRecordLength.store(mRecordLength);
    }
}

void YawnEngine::renderLoop(float* left, float* right, int numFrames) {
    if (!mPlayingLoop || mPlayEnd <= mPlayStart) return;
    const int loopLength = mPlayEnd - mPlayStart;
    const int crossfade = std::min(mCrossfadeSamples, loopLength / 2);
    const int crossfadeStart = mPlayEnd - crossfade;
    for (int i = 0; i < numFrames; ++i) {
        if (mPlayIndex < mPlayStart || mPlayIndex >= mPlayEnd) {
            mPlayIndex = mPlayStart;
        }
        float sampleL;
        float sampleR;
        if (crossfade > 0 && mPlayIndex >= crossfadeStart) {
            const int k = mPlayIndex - crossfadeStart;
            const float t = static_cast<float>(k + 1) /
                            static_cast<float>(crossfade);
            const int head = mPlayStart + k;
            sampleL = mRecordBuffer[mPlayIndex * 2] * (1.0f - t) +
                      mRecordBuffer[head * 2] * t;
            sampleR = mRecordBuffer[mPlayIndex * 2 + 1] * (1.0f - t) +
                      mRecordBuffer[head * 2 + 1] * t;
            ++mPlayIndex;
            if (mPlayIndex >= mPlayEnd) {
                mPlayIndex = mPlayStart + crossfade;
            }
        } else {
            sampleL = mRecordBuffer[mPlayIndex * 2];
            sampleR = mRecordBuffer[mPlayIndex * 2 + 1];
            ++mPlayIndex;
            if (mPlayIndex >= mPlayEnd) {
                mPlayIndex = mPlayStart + crossfade;
            }
        }
        float startGain = 1.0f;
        if (mPlayFadeRemaining > 0 && mPlayFadeSamples > 0) {
            startGain = 1.0f - static_cast<float>(mPlayFadeRemaining) /
                               static_cast<float>(mPlayFadeSamples);
            --mPlayFadeRemaining;
        }
        left[i * 2] += sampleL * startGain;
        right[i * 2] += sampleR * startGain;
    }
    mLoopPlayIndex.store(mPlayIndex);
}

void YawnEngine::recordSamples(const float* left, const float* right,
                               int numFrames) {
    if (!mRecording) return;
    for (int i = 0; i < numFrames; ++i) {
        if (mRecordLength >= mRecordCapacity) {
            mRecording = false;
            mLoopRecording.store(false);
            mLoopRecordLength.store(mRecordLength);
            return;
        }
        const int base = mRecordLength * 2;
        mRecordBuffer[base] = left[i * 2];
        mRecordBuffer[base + 1] = right[i * 2];
        ++mRecordLength;
    }
    mLoopRecordLength.store(mRecordLength);
}

void YawnEngine::clearActiveNotes() {
    for (auto& channel : mActiveNotes) {
        channel.fill(false);
    }
    mPitchModulation.resetNotes();
}

void YawnEngine::updateActiveNotes(
        const yawn::midi::MidiBuffer& buffer) {
    for (const auto& msg : buffer) {
        const int channel = msg.channel & 15;
        const int note = msg.note & 127;
        if (msg.type == yawn::midi::MidiMessage::Type::NoteOn) {
            mActiveNotes[channel][note] = msg.velocity > 0;
            if (msg.velocity > 0) {
                mPitchModulation.markActive(channel, note);
            } else {
                mPitchModulation.noteOff(channel, note);
            }
        } else if (msg.type == yawn::midi::MidiMessage::Type::NoteOff) {
            mActiveNotes[channel][note] = false;
            mPitchModulation.noteOff(channel, note);
        } else if (msg.type == yawn::midi::MidiMessage::Type::ControlChange
                && (msg.ccNumber == 120 || msg.ccNumber == 123)) {
            clearActiveNotes();
        }
    }
}

int YawnEngine::activeNoteCount() const {
    int count = 0;
    for (const auto& channel : mActiveNotes) {
        for (bool active : channel) {
            count += active ? 1 : 0;
        }
    }
    return count;
}

void YawnEngine::sendPerNotePitch(int channel, int note, float semitones) {
    const float normalized = std::clamp(
        semitones / static_cast<float>(MicrotonalBendRange), -1.0f, 1.0f);
    mMidiBuffer.addMessage(yawn::midi::MidiMessage::perNotePitchBend(
        channel, note, yawn::midi::Convert::floatToPb32(normalized)));
}

bool YawnEngine::applyModTarget(int type, int slot, int param, float offset,
                                bool restoreBase) {
    if (type == 0) {
        const auto* info = instrumentInfo(param);
        if (!info || !mInstrument) {
            return false;
        }
        const float base = currentInstrumentParams().values[param].load();
        const float value = restoreBase
            ? base
            : base + offset * (info->maxValue - info->minValue) * 0.5f;
        mInstrument->setParameter(param, clampParam(*info, value));
        return true;
    }
    if (type == 1) {
        if (slot < 0 || slot >= mMidiChainCount || !mMidiChain[slot]
                || !mMidiChain[slot]->effect) {
            return false;
        }
        const auto* info = midiChainInfo(slot, param);
        if (!info) {
            return false;
        }
        const float base = mMidiChain[slot]->params.values[param].load();
        const float value = restoreBase
            ? base
            : base + offset * (info->maxValue - info->minValue) * 0.5f;
        mMidiChain[slot]->effect->setParameter(
            param, clampParam(*info, value));
        return true;
    }
    if (type == 2) {
        if (slot < 0 || slot >= mAudioChainCount || !mAudioChain[slot]
                || !mAudioChain[slot]->effect) {
            return false;
        }
        const auto* info = audioChainInfo(slot, param);
        if (!info) {
            return false;
        }
        const float base = mAudioChain[slot]->params.values[param].load();
        const float value = restoreBase
            ? base
            : base + offset * (info->maxValue - info->minValue) * 0.5f;
        mAudioChain[slot]->effect->setParameter(
            param, clampParam(*info, value));
        return true;
    }
    return false;
}

void YawnEngine::applyModulation() {
    for (int source = 0; source < kModSourceCount; ++source) {
        const float target = mModSources[source].load(
            std::memory_order_relaxed);
        mModSmoothed[source] +=
            mModSmoothCoeff * (target - mModSmoothed[source]);
    }

    std::array<ModTarget, kMaxModSlots> current{};
    std::array<float, kMaxModSlots> offsets{};
    int count = 0;
    float pitchOffset = 0.0f;
    for (const auto& modSlot : mModSlots) {
        if (!modSlot.enabled || modSlot.targetType < 0) {
            continue;
        }
        const float offset = modSlot.amount *
            mModSmoothed[std::clamp(modSlot.source, 0, kModSourceCount - 1)];
        if (modSlot.targetType == 3) {
            pitchOffset += offset;
            continue;
        }
        int found = -1;
        for (int i = 0; i < count; ++i) {
            if (current[i].type == modSlot.targetType
                    && current[i].slot == modSlot.targetSlot
                    && current[i].param == modSlot.targetParam) {
                found = i;
                break;
            }
        }
        if (found < 0 && count < kMaxModSlots) {
            found = count++;
            current[found] = {modSlot.targetType, modSlot.targetSlot,
                              modSlot.targetParam};
            offsets[found] = 0.0f;
        }
        if (found >= 0) {
            offsets[found] += offset;
        }
    }

    for (int i = 0; i < count; ++i) {
        applyModTarget(current[i].type, current[i].slot, current[i].param,
                       offsets[i], false);
    }
    for (int i = 0; i < mLastModTargetCount; ++i) {
        const ModTarget& old = mLastModTargets[i];
        bool stillActive = false;
        for (int j = 0; j < count; ++j) {
            if (current[j].type == old.type && current[j].slot == old.slot
                    && current[j].param == old.param) {
                stillActive = true;
                break;
            }
        }
        if (!stillActive) {
            applyModTarget(old.type, old.slot, old.param, 0.0f, true);
        }
    }
    mLastModTargets = current;
    mLastModTargetCount = count;
    mPitchModulation.setNormalizedOffset(pitchOffset);
}

void YawnEngine::copyWaveform(float* out, int length) const {
    if (out == nullptr || length <= 0) {
        return;
    }
    const int count = std::min(length, kWaveformSize);
    const int start =
        (mWaveformIndex - count + kWaveformSize) % kWaveformSize;
    for (int i = 0; i < count; ++i) {
        out[i] = mWaveform[(start + i) % kWaveformSize];
    }
}

oboe::DataCallbackResult YawnEngine::onAudioReady(
    oboe::AudioStream* /* stream */, void* audioData, int32_t numFrames) {
    if (numFrames <= 0) return oboe::DataCallbackResult::Continue;
    const auto callbackStart = std::chrono::steady_clock::now();

    float* output = static_cast<float*>(audioData);
    processEvents(numFrames);
    applyModulation();

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

        for (int fx = 0; fx < mMidiChainCount; ++fx) {
            auto& slot = mMidiChain[fx];
            if (slot && slot->effect && !slot->bypassed.load()) {
                slot->effect->process(mMidiBuffer, frames, mTransport);
            }
        }

        std::fill(mMixBuffer.begin(), mMixBuffer.begin() +
                  static_cast<size_t>(frames) * 2, 0.0f);
        float* left = mMixBuffer.data();
        float* right = mMixBuffer.data() + 1;

        updateActiveNotes(mMidiBuffer);
        if (mPitchModulation.consumeDirty()) {
            int pitchChannels[volna::PitchModulation::kChannels
                              * volna::PitchModulation::kNotes];
            int pitchNotes[volna::PitchModulation::kChannels
                           * volna::PitchModulation::kNotes];
            float pitchSemitones[volna::PitchModulation::kChannels
                                 * volna::PitchModulation::kNotes];
            const int updates = mPitchModulation.collectUpdates(
                    pitchChannels, pitchNotes, pitchSemitones,
                    volna::PitchModulation::kChannels
                            * volna::PitchModulation::kNotes);
            for (int i = 0; i < updates; ++i) {
                sendPerNotePitch(pitchChannels[i], pitchNotes[i],
                                 pitchSemitones[i]);
            }
        }
        const int active = std::clamp(activeNoteCount(), 0, 16);
        const int effectiveVoices = std::max(1, active);
        const float polyTarget = mPolyphonyLeveling.load()
            ? 1.0f / std::sqrt(static_cast<float>(effectiveVoices))
            : 1.0f;
        const float polySmooth = 1.0f - std::exp(
            -1.0f / (0.02f * static_cast<float>(mSampleRate)));

        if (mInstrument) {
            mInstrument->process(mMixBuffer.data(), frames, 2, mMidiBuffer);
        }
        for (int fx = 0; fx < mAudioChainCount; ++fx) {
            auto& slot = mAudioChain[fx];
            if (slot && slot->effect) {
                slot->effect->setTempo(mTransport.bpm, mBeatPosition, true);
                if (!slot->bypassed.load()) {
                    slot->effect->process(mMixBuffer.data(), frames, 2);
                }
            }
        }

        renderLoop(left, right, frames);

        for (int i = 0; i < frames; ++i) {
            mPolyphonyGain += polySmooth * (polyTarget - mPolyphonyGain);
            const float l = std::tanh(
                left[i * 2] * mMasterVolume * mPolyphonyGain);
            const float r = std::tanh(
                right[i * 2] * mMasterVolume * mPolyphonyGain);
            if (mChannelCount > 1) {
                output[(offset + i) * mChannelCount] = l;
                output[(offset + i) * mChannelCount + 1] = r;
            } else {
                output[offset + i] = (l + r) * 0.5f;
            }
            mWaveform[mWaveformIndex] = l;
            mWaveformIndex = (mWaveformIndex + 1) % kWaveformSize;
        }
        recordSamples(left, right, frames);

        mMidiBuffer.clear();
        mBeatPosition += static_cast<double>(frames) / mSamplesPerBeat;
        mSamplePosition += frames;
        remaining -= frames;
        offset += frames;
        firstChunk = false;
    }

    if (mSafetyLimiter.load()) {
        mSafetyLimiterEffect.process(output, numFrames, mChannelCount);
    }

    const double elapsed = std::chrono::duration<double>(
        std::chrono::steady_clock::now() - callbackStart).count();
    const double budget = static_cast<double>(numFrames) /
                          static_cast<double>(mSampleRate);
    if (budget > 0.0) {
        const float instant = static_cast<float>(elapsed / budget);
        const float previous = mDspLoad.load(std::memory_order_relaxed);
        mDspLoad.store(previous * 0.8f + instant * 0.2f,
                       std::memory_order_relaxed);
        if (instant > mDspPeak.load(std::memory_order_relaxed)) {
            mDspPeak.store(instant, std::memory_order_relaxed);
        }
    }
    return oboe::DataCallbackResult::Continue;
}

void YawnEngine::onErrorBeforeClose(oboe::AudioStream* /* stream */,
                                    oboe::Result /* error */) {}

void YawnEngine::onErrorAfterClose(oboe::AudioStream* /* stream */,
                                   oboe::Result /* error */) {
    if (mInitialized.load() && !mSuspended.load()) {
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
        currentInstrumentParams().values[0].store(
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
