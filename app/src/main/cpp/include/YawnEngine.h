#ifndef VolnaYawnEngine_h_
#define VolnaYawnEngine_h_

#include <oboe/Oboe.h>

#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "EngineEventQueue.h"
#include "effects/AudioEffect.h"
#include "instruments/Instrument.h"
#include "midi/MidiEffect.h"
#include "midi/MidiTypes.h"

// A compact instrument host built from YAWN's DSP modules:
//   MIDI effects -> wavetable/subtractive instrument -> audio effects -> loop recorder
class YawnEngine : public oboe::AudioStreamCallback {
public:
    static constexpr int kMaxFrames = 4096;
    static constexpr int kMaxParams = 64;
    static constexpr int kInstrumentCount = 2;
    static constexpr int kMidiEffectCount = 8;
    static constexpr int kAudioEffectCount = 8;
    static constexpr int kMaxMidiChainSlots = 8;
    static constexpr int kMaxAudioChainSlots = 8;

    enum class InstrumentKind : int {
        Wavetable = 0,
        Subtractive = 1,
    };

    YawnEngine();
    ~YawnEngine() override;

    void init(float sampleRate, int instrumentIndex);
    void shutdown();
    void pauseAudio();
    void resumeAudio();

    bool initialized() const { return mInitialized; }

    int instrumentIndex() const { return mInstrumentIndex; }
    bool selectInstrument(int index);
    static const char* instrumentName(int index);
    static const char* instrumentId(int index);

    int instrumentParamCount() const;
    const char* instrumentParamName(int index) const;
    float instrumentParamMin(int index) const;
    float instrumentParamMax(int index) const;
    float instrumentParamDefault(int index) const;
    bool instrumentParamIsBoolean(int index) const;
    int instrumentParamLabelCount(int index) const;
    const char* instrumentParamLabel(int index, int label) const;
    float instrumentParamValue(int index) const;
    void setInstrumentParam(int index, float value);

    // Effect type catalog (used by the Add-effect picker).
    static int midiEffectCount() { return kMidiEffectCount; }
    static const char* midiEffectName(int index);
    static const char* midiEffectId(int index);
    int midiEffectParamCount(int index) const;
    const char* midiEffectParamName(int effect, int index) const;
    float midiEffectParamMin(int effect, int index) const;
    float midiEffectParamMax(int effect, int index) const;
    float midiEffectParamDefault(int effect, int index) const;
    bool midiEffectParamIsBoolean(int effect, int index) const;
    int midiEffectParamLabelCount(int effect, int index) const;
    const char* midiEffectParamLabel(int effect, int index, int label) const;
    float midiEffectParamValue(int effect, int index) const;
    void setMidiEffectParam(int effect, int index, float value);
    bool midiEffectBypassed(int index) const;
    void setMidiEffectBypassed(int index, bool bypassed);

    static int audioEffectCount() { return kAudioEffectCount; }
    static const char* audioEffectName(int index);
    static const char* audioEffectId(int index);
    int audioEffectParamCount(int index) const;
    const char* audioEffectParamName(int effect, int index) const;
    float audioEffectParamMin(int effect, int index) const;
    float audioEffectParamMax(int effect, int index) const;
    float audioEffectParamDefault(int effect, int index) const;
    bool audioEffectParamIsBoolean(int effect, int index) const;
    int audioEffectParamLabelCount(int effect, int index) const;
    const char* audioEffectParamLabel(int effect, int index, int label) const;
    float audioEffectParamValue(int effect, int index) const;
    void setAudioEffectParam(int effect, int index, float value);
    bool audioEffectBypassed(int index) const;
    void setAudioEffectBypassed(int index, bool bypassed);

    // MIDI effect chain.
    int midiChainSize() const { return mMidiChainCount; }
    const char* midiChainName(int slot) const;
    const char* midiChainId(int slot) const;
    bool midiChainBypassed(int slot) const;
    void setMidiChainBypassed(int slot, bool bypassed);
    int addMidiEffect(int type);
    bool removeMidiEffect(int slot);
    bool moveMidiEffect(int slot, int delta);
    int midiChainParamCount(int slot) const;
    const char* midiChainParamName(int slot, int index) const;
    float midiChainParamMin(int slot, int index) const;
    float midiChainParamMax(int slot, int index) const;
    float midiChainParamDefault(int slot, int index) const;
    bool midiChainParamIsBoolean(int slot, int index) const;
    int midiChainParamLabelCount(int slot, int index) const;
    const char* midiChainParamLabel(int slot, int index, int label) const;
    float midiChainParamValue(int slot, int index) const;
    void setMidiChainParam(int slot, int index, float value);

    // Audio effect chain.
    int audioChainSize() const { return mAudioChainCount; }
    const char* audioChainName(int slot) const;
    const char* audioChainId(int slot) const;
    bool audioChainBypassed(int slot) const;
    void setAudioChainBypassed(int slot, bool bypassed);
    int addAudioEffect(int type);
    bool removeAudioEffect(int slot);
    bool moveAudioEffect(int slot, int delta);
    int audioChainParamCount(int slot) const;
    const char* audioChainParamName(int slot, int index) const;
    float audioChainParamMin(int slot, int index) const;
    float audioChainParamMax(int slot, int index) const;
    float audioChainParamDefault(int slot, int index) const;
    bool audioChainParamIsBoolean(int slot, int index) const;
    int audioChainParamLabelCount(int slot, int index) const;
    const char* audioChainParamLabel(int slot, int index, int label) const;
    float audioChainParamValue(int slot, int index) const;
    void setAudioChainParam(int slot, int index, float value);

    // Full synth state (instrument banks, chains, bypass, params, tempo).
    std::string saveState() const;
    bool restoreState(const std::string& json, std::string* error);

    float tempo() const { return static_cast<float>(mTransport.bpm); }
    float masterVolume() const { return mMasterVolume; }
    void setTempo(float bpm);
    void setMasterVolume(float volume);

    void noteOn(int channel, int note, int velocity);
    void noteOff(int channel, int note, int velocity);
    void notePitch(int channel, int note, float bendSemitones);
    void pitchBend(int channel, int lsb, int msb);
    void controlChange(int channel, int control, int value);
    void polyPressure(int channel, int note, int pressure);
    void channelPressure(int channel, int pressure);
    void allNotesOff();
    void selectWaveform(int channel, int osc, int note, int wave);
    void noteSpread(int channel, int note, int spread);

    void setPlay(bool play);
    void setRecord(bool record);

    void setDelayLevel(int level);
    void setDelayTime(int samples);
    void setDelayFeedback(int feedback);

    bool loadWavetable(const std::string& path, std::string* error);
    bool saveWavetable(const std::string& path, std::string* error) const;
    void clearWavetable();
    bool hasCustomWavetable() const;
    float* wavetableFrame(float position, float* out, int length) const;

    const float* waveformData() const { return mWaveform; }
    float dspLoad() const { return mDspLoad.load(std::memory_order_relaxed); }
    float dspPeak() const { return mDspPeak.load(std::memory_order_relaxed); }
    void resetDspPeak() { mDspPeak.store(0.0f, std::memory_order_relaxed); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream,
                                          void* audioData,
                                          int32_t numFrames) override;
    void onErrorBeforeClose(oboe::AudioStream* stream,
                            oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream,
                           oboe::Result error) override;

private:
    struct ParamState {
        std::array<std::atomic<float>, kMaxParams> values{};
    };

    struct MidiChainSlot {
        std::unique_ptr<yawn::midi::MidiEffect> effect;
        ParamState params;
        std::atomic<bool> bypassed{true};
        int type = 0;
    };

    struct AudioChainSlot {
        std::unique_ptr<yawn::effects::AudioEffect> effect;
        ParamState params;
        std::atomic<bool> bypassed{true};
        int type = 0;
    };

    void oboeInit();
    bool audioIsRunning() const;
    void push(const EngineEvent& event);
    void processEvents(int numFrames);
    void dispatchEvent(const EngineEvent& event, int numFrames);

    std::unique_ptr<yawn::instruments::Instrument> createInstrument(int index) const;
    ParamState& currentInstrumentParams();
    const ParamState& currentInstrumentParams() const;
    void initInstrumentBanks();
    std::unique_ptr<yawn::midi::MidiEffect> createMidiEffect(int type) const;
    std::unique_ptr<yawn::effects::AudioEffect> createAudioEffect(int type) const;
    void applyInstrumentDefaults();
    void initMidiChain();
    void initAudioChain();
    void clearMidiChain();
    void clearAudioChain();
    void resetChainEffects();
    int appendMidiEffect(int type, bool bypassed);
    int appendAudioEffect(int type, bool bypassed);

    void handleNoteOn(int channel, int note, int velocity);
    void handleNoteOff(int channel, int note);
    void handlePitchBend(int channel, int value14);
    void handleControlChange(int channel, int control, int value);
    void handleAllNotesOff();

    void doSetPlay(bool play);
    void doSetRecord(bool record);
    void renderLoop(float* left, float* right, int numFrames);
    void recordSamples(const float* left, const float* right, int numFrames);

    const yawn::ParameterInfo* instrumentInfo(int index) const;
    const yawn::ParameterInfo* midiInfo(int effect, int index) const;
    const yawn::ParameterInfo* audioInfo(int effect, int index) const;
    const yawn::ParameterInfo* midiChainInfo(int slot, int index) const;
    const yawn::ParameterInfo* audioChainInfo(int slot, int index) const;
    int findAudioChainSlot(int type) const;
    void applyAudioChainParam(int slot, int index, float value);

    std::unique_ptr<yawn::instruments::Instrument> mInstrument;
    std::unique_ptr<yawn::instruments::Instrument> mMetadataInstruments[kInstrumentCount];
    std::array<ParamState, kInstrumentCount> mInstrumentParamsByType;
    std::array<std::unique_ptr<yawn::midi::MidiEffect>, kMidiEffectCount> mMidiCatalog;
    std::array<std::unique_ptr<yawn::effects::AudioEffect>, kAudioEffectCount> mAudioCatalog;

    std::array<std::unique_ptr<MidiChainSlot>, kMaxMidiChainSlots> mMidiChain;
    int mMidiChainCount = 0;
    std::array<std::unique_ptr<AudioChainSlot>, kMaxAudioChainSlots> mAudioChain;
    int mAudioChainCount = 0;

    EngineEventQueue mEvents;
    yawn::midi::MidiBuffer mMidiBuffer;
    yawn::midi::TransportInfo mTransport;
    std::vector<float> mMixBuffer;
    float mWaveform[256] = {};
    int mWaveformIndex = 0;

    oboe::ManagedStream mStream;
    std::mutex mControlMutex;
    std::atomic<bool> mInitialized{false};
    std::atomic<bool> mSuspended{false};
    std::atomic<float> mDspLoad{0.0f};
    std::atomic<float> mDspPeak{0.0f};
    std::atomic<bool> mInstrumentDirty{false};
    int mInstrumentIndex = 0;
    int mSampleRate = 48000;
    int mChannelCount = 2;
    float mMasterVolume = 0.8f;
    double mBeatPosition = 0.0;
    double mSamplesPerBeat = 24000.0;
    int64_t mSamplePosition = 0;

    std::vector<float> mRecordBuffer;
    int mRecordLength = 0;
    int mRecordCapacity = 0;
    int mPlayStart = 0;
    int mPlayEnd = 0;
    int mPlayIndex = 0;
    bool mRecording = false;
    bool mPlayingLoop = false;
    int mFadeSamples = 0;

    std::mutex mWavetableMutex;
    std::string mWavetablePath;
};

#endif
