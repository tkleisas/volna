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

    void setTempo(float bpm);
    void setMasterVolume(float volume);

    void noteOn(int channel, int note, int velocity);
    void noteOff(int channel, int note, int velocity);
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

    void oboeInit();
    bool audioIsRunning() const;
    void push(const EngineEvent& event);
    void processEvents(int numFrames);
    void dispatchEvent(const EngineEvent& event, int numFrames);

    std::unique_ptr<yawn::instruments::Instrument> createInstrument(int index) const;
    void applyInstrumentDefaults();
    void applyMidiEffectDefaults();
    void applyAudioEffectDefaults();
    void initMidiEffects();
    void initAudioEffects();

    void handleNoteOn(int channel, int note, int velocity);
    void handleNoteOff(int channel, int note);
    void handlePitchBend(int channel, int value14);
    void handleControlChange(int channel, int control, int value);
    void handleAllNotesOff();

    void doSetPlay(bool play);
    void doSetRecord(bool record);
    void renderLoop(float* left, float* right, int numFrames);
    void recordSamples(const float* left, const float* right, int numFrames);

    static void clampFrameIndexes(int effect, int index, int& paramCount);
    const yawn::ParameterInfo* instrumentInfo(int index) const;
    const yawn::ParameterInfo* midiInfo(int effect, int index) const;
    const yawn::ParameterInfo* audioInfo(int effect, int index) const;

    std::unique_ptr<yawn::instruments::Instrument> mInstrument;
    std::unique_ptr<yawn::instruments::Instrument> mMetadataInstruments[kInstrumentCount];
    std::array<std::unique_ptr<yawn::midi::MidiEffect>, kMidiEffectCount> mMidiEffects;
    std::array<std::unique_ptr<yawn::effects::AudioEffect>, kAudioEffectCount> mAudioEffects;
    std::array<std::atomic<bool>, kMidiEffectCount> mMidiBypass{};
    std::array<std::atomic<bool>, kAudioEffectCount> mAudioBypass{};
    ParamState mInstrumentParams;
    std::array<ParamState, kMidiEffectCount> mMidiParams;
    std::array<ParamState, kAudioEffectCount> mAudioParams;

    EngineEventQueue mEvents;
    yawn::midi::MidiBuffer mMidiBuffer;
    yawn::midi::TransportInfo mTransport;
    std::vector<float> mMixBuffer;
    float mWaveform[256] = {};
    int mWaveformIndex = 0;

    oboe::ManagedStream mStream;
    std::mutex mControlMutex;
    std::atomic<bool> mInitialized{false};
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
