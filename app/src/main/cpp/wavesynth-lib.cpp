#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <locale>
#include <random>
#include <string>
#include <vector>

#include "VAEngine.h"
#include "YawnEngine.h"
#include <oboe/Oboe.h>

#define WTLEN 256
#define WTCOUNT 256

#define POLYPHONY 10

VAEngine<POLYPHONY, WTCOUNT, WTLEN>* engine = nullptr;
YawnEngine* gYawnEngine = nullptr;
std::atomic<int> gActiveEngine{0};
int gYawnInstrumentIndex = 0;
constexpr int kEngineLegacy = 0;
constexpr int kEngineYawn = 1;

float Waveforms[WTCOUNT * WTLEN];
float FactoryWaveforms[WTCOUNT * WTLEN];
float CustomWaveforms[WTCOUNT * WTLEN];
bool hasCustomBank = false;
std::mt19937 eng{std::random_device()()};
float wavedata[256];
float data[256];
int index = 0;
int sr = 0;

void initWaveforms(bool bandlimit, float freq, float q) {
    float sintable[WTLEN];
    float tritable[WTLEN];
    float squtable[WTLEN];
    float sawtable[WTLEN];
    float rndtable[WTLEN];
    LowPass lp;
    lp.SetParameters(freq, q);
    float max = 0.8;
    for (int i = 0; i < WTLEN; i++) {
        sintable[i] = max * sin(2.0 * (M_PI / (float)WTLEN) * i);
    }
    if (bandlimit) {
        lp.reset();
        for (int i = 0; i < 256; i++) {
            sintable[i] = lp.Process(sintable[i]);
        }
    }

    for (int i = 0; i < WTLEN / 4; i++) {
        tritable[i] = i * (max / (WTLEN / 4));
    }
    for (int i = 0; i < 128; i++) {
        tritable[i + 64] = max * (+1.0 - i * (2.0 / ((double)WTLEN / 2)));
    }

    for (int i = 0; i < 64; i++) {
        tritable[i + 128 + 64] = max * (-1.0 + i * (1.0 / ((double)WTLEN / 2)));
    }
    if (bandlimit) {
        lp.reset();
        for (int i = 0; i < 256; i++) {
            tritable[i] = lp.Process(tritable[i]);
        }
    }

    for (int i = 0; i < WTLEN; i++) {
        squtable[i] = max * (i < (WTLEN / 2) ? 1.0 : -1.0);
    }
    if (bandlimit) {
        lp.reset();
        for (int i = 0; i < WTLEN; i++) {
            squtable[i] = lp.Process(squtable[i]);
        }
    }

    for (int i = 0; i < 256; i++) {
        sawtable[i] = max * ((-1.0 + (2.0 / WTLEN) * i));
    }
    if (bandlimit) {
        lp.reset();
        for (int i = 0; i < WTLEN; i++) {
            sawtable[i] = lp.Process(sawtable[i]);
        }
    }
    std::uniform_real_distribution<double> dice{-1, 1};

    for (int i = 0; i < 256; i++) {
        float r = (float)dice(eng);
        rndtable[i] = max * r;
    }
    if (bandlimit) {
        lp.reset();
        for (int i = 0; i < WTLEN; i++) {
            rndtable[i] = lp.Process(rndtable[i]);
        }
    }
    float sinf = 1.0;
    float trif = 0.0;
    float squf = 0.0;
    float sawf = 0.0;
    float rndf = 0.0;
    float l = (float)WTCOUNT / 5.0;
    int counter = 0;
    float f = (1.0 / (float)l);
    for (int w = 0; w < WTCOUNT; w++) {
        for (int i = 0; i < 256; i++) {
            Waveforms[w * WTLEN + i] = sinf * sintable[i] + trif * tritable[i] +
                                       squf * squtable[i] + sawf * sawtable[i] +
                                       rndf * rndtable[i];
        }
        if (counter <= l) {
            sinf = sinf - f;
            trif = trif + f;
        } else if (counter > l && counter <= 2 * l) {
            sinf = 0.0;
            trif = trif - f;
            squf = squf + f;
        } else if (counter >= 2 * l && counter < 3 * l) {
            trif = 0.0;
            squf = squf - f;
            sawf = sawf + f;
        } else if (counter >= 3 * l && counter < 4 * l) {
            squf = 0.0;
            sawf = sawf - f;
            rndf = rndf + f;
        } else {
            sawf = 0.0;
            rndf = rndf - f;
            sinf = sinf + f;
        }
        counter++;
    }

    std::memcpy(FactoryWaveforms, Waveforms, sizeof(Waveforms));
}

static bool applyWavetableBank(const float* bank) {
    if (bank == nullptr) {
        return false;
    }
    if (engine != nullptr) {
        if (!engine->setWavetableBank(bank)) {
            return false;
        }
    } else {
        std::memcpy(Waveforms, bank, sizeof(Waveforms));
    }
    std::memcpy(CustomWaveforms, bank, sizeof(CustomWaveforms));
    hasCustomBank = true;
    return true;
}

static YawnEngine* activeYawnEngine() {
    return gActiveEngine.load() == kEngineYawn ? gYawnEngine : nullptr;
}

static void ensureLegacyEngine(int sample_rate) {
    if (engine != nullptr) {
        return;
    }
    initWaveforms(true, 0.5, 0.6);
    if (hasCustomBank) {
        std::memcpy(Waveforms, CustomWaveforms, sizeof(Waveforms));
    }
    engine = new VAEngine<POLYPHONY, WTCOUNT, WTLEN>(Waveforms, FactoryWaveforms);
    sr = sample_rate > 0 ? sample_rate : 48000;
    engine->init((float)sr);
}

static void ensureYawnEngine(int sample_rate, int instrumentIndex) {
    gYawnInstrumentIndex = instrumentIndex;
    if (gYawnEngine == nullptr) {
        gYawnEngine = new YawnEngine();
        gYawnEngine->init(sample_rate > 0 ? (float)sample_rate : 48000.0f,
                          instrumentIndex);
    } else {
        gYawnEngine->selectInstrument(instrumentIndex);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_initVAEngine(JNIEnv* /* env */,
                                                     jobject /* thiz */,
                                                     jint sample_rate) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->pauseAudio();
    }
    ensureLegacyEngine(sample_rate);
    gActiveEngine.store(kEngineLegacy);
    engine->resumeAudio();
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_initYawnEngine(JNIEnv* /* env */,
                                                       jobject /* thiz */,
                                                       jint sample_rate,
                                                       jint instrument_index) {
    if (engine != nullptr) {
        engine->pauseAudio();
    }
    ensureYawnEngine(sample_rate, instrument_index);
    gActiveEngine.store(kEngineYawn);
    gYawnEngine->resumeAudio();
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_switchEngine(JNIEnv* /* env */,
                                                     jobject /* thiz */,
                                                     jint mode,
                                                     jint instrument_index) {
    if (mode == kEngineLegacy) {
        if (gYawnEngine != nullptr) {
            gYawnEngine->pauseAudio();
        }
        ensureLegacyEngine(sr);
        gActiveEngine.store(kEngineLegacy);
        engine->resumeAudio();
        return kEngineLegacy;
    }

    if (engine != nullptr) {
        engine->pauseAudio();
    }
    ensureYawnEngine(sr, instrument_index);
    gActiveEngine.store(kEngineYawn);
    gYawnEngine->resumeAudio();
    return kEngineYawn;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_selectYawnInstrument(
    JNIEnv* /* env */, jobject /* thiz */, jint instrument_index) {
    ensureYawnEngine(sr, instrument_index);
    gYawnInstrumentIndex = instrument_index;
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_shutdownVAEngine(JNIEnv* /* env */,
                                                        jobject /* thiz */) {
    delete engine;
    engine = nullptr;
    delete gYawnEngine;
    gYawnEngine = nullptr;
    gActiveEngine.store(kEngineLegacy);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setVAEngineDefaultStreamValues(
    JNIEnv* /* env */, jobject /* thiz */, jint sample_rate,
    jint frames_per_burst) {
    oboe::DefaultStreamValues::SampleRate = (int32_t)sample_rate;
    oboe::DefaultStreamValues::FramesPerBurst = (int32_t)frames_per_burst;
    sr = sample_rate;
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_pauseAudio(JNIEnv* /* env */,
                                                   jobject /* thiz */) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->pauseAudio();
    } else if (engine != nullptr) {
        engine->pauseAudio();
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_resumeAudio(JNIEnv* /* env */,
                                                    jobject /* thiz */) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->resumeAudio();
    } else if (engine != nullptr) {
        engine->resumeAudio();
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendMidiCC(JNIEnv* /* env */,
                                                   jobject /* thiz */,
                                                   jint channel, jint cc,
                                                   jint data) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->controlChange(channel, cc, data);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleControlChange((uint8_t)channel, (uint8_t)cc, (uint8_t)data);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendMidiNoteOn(JNIEnv* /* env */,
                                                       jobject /* thiz */,
                                                       jint channel, jint note,
                                                       jint velocity) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->noteOn(channel, note, velocity);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleNoteOn(channel, note, velocity);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendMidiNoteOff(JNIEnv* /* env */,
                                                        jobject /* thiz */,
                                                        jint channel,
                                                        jint note,
                                                        jint velocity) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->noteOff(channel, note, velocity);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleNoteOff(channel, note, velocity);
    return 0;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getDspLoad(JNIEnv* /* env */,
                                                   jobject /* thiz */) {
    if (activeYawnEngine() != nullptr) {
        return gYawnEngine->dspLoad();
    }
    return engine != nullptr ? engine->getDspLoad() : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getDspPeak(JNIEnv* /* env */,
                                                   jobject /* thiz */) {
    if (activeYawnEngine() != nullptr) {
        return gYawnEngine->dspPeak();
    }
    return engine != nullptr ? engine->getDspPeak() : 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_resetDspPeak(JNIEnv* /* env */,
                                                     jobject /* thiz */) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->resetDspPeak();
    } else if (engine != nullptr) {
        engine->resetDspPeak();
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendMidiNotePitch(
    JNIEnv* /* env */, jobject /* thiz */, jint channel, jint note,
    jfloat bend_semitones) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->notePitch(channel, note, bend_semitones);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleNotePitch(channel, note, bend_semitones);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendMidiNoteSpread(JNIEnv* /* env */,
                                                           jobject /* thiz */,
                                                           jint channel,
                                                           jint note,
                                                           jint spread) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->noteSpread(channel, note, spread);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleNoteSpread(channel, note, spread);
    return 0;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getWaveform(JNIEnv* env,
                                                    jobject /* thiz */) {
    jfloatArray result = env->NewFloatArray(256);
    if (result == nullptr) {
        return nullptr;
    }
    if (activeYawnEngine() != nullptr) {
        env->SetFloatArrayRegion(result, 0, 256, gYawnEngine->waveformData());
        return result;
    }
    if (engine == nullptr) {
        return result;
    }
    for (int i = 0; i < 256; i++) {
        data[i] = engine->getWavedata()[i];
    }
    env->SetFloatArrayRegion(result, 0, 256, data);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_selectWaveform(JNIEnv* /* env */,
                                                       jobject /* thiz */,
                                                       jint channel, jint osc,
                                                       jint note, jint wave) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->selectWaveform(channel, osc, note, wave);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleSelectWaveform((uint8_t)channel, (uint8_t)osc, (uint8_t)note,
                                 (uint8_t)wave);
    return 0;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getWavetable(JNIEnv* env,
                                                     jobject /* thiz */,
                                                     jint index) {
    jfloatArray result = env->NewFloatArray(256);
    if (result == nullptr) {
        return nullptr;
    }
    if (activeYawnEngine() != nullptr) {
        float frame[256];
        gYawnEngine->wavetableFrame(static_cast<float>(std::clamp(index, 0, WTCOUNT - 1)) /
                                        static_cast<float>(WTCOUNT - 1),
                                    frame, 256);
        env->SetFloatArrayRegion(result, 0, 256, frame);
        return result;
    }
    if (index < 0 || index >= WTCOUNT) {
        return result;
    }
    env->SetFloatArrayRegion(result, 0, WTLEN, &Waveforms[index * WTLEN]);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setWavetable(JNIEnv* env,
                                                     jobject /* thiz */,
                                                     jint index,
                                                     jfloatArray wavetable) {
    if (activeYawnEngine() != nullptr) {
        return -1;
    }
    if (index < 0 || index >= WTCOUNT || wavetable == nullptr) {
        return -1;
    }
    if (env->GetArrayLength(wavetable) < WTLEN) {
        return -1;
    }
    std::vector<float> bank(Waveforms, Waveforms + WTCOUNT * WTLEN);
    env->GetFloatArrayRegion(wavetable, 0, WTLEN,
                             &bank[index * WTLEN]);
    return applyWavetableBank(bank.data()) ? 0 : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setWavetableBank(JNIEnv* env,
                                                         jobject /* thiz */,
                                                         jfloatArray bank) {
    if (activeYawnEngine() != nullptr) {
        return -1;
    }
    if (bank == nullptr) {
        return -1;
    }
    const jsize length = env->GetArrayLength(bank);
    if (length != WTCOUNT * WTLEN) {
        return -1;
    }
    std::vector<float> data(length);
    env->GetFloatArrayRegion(bank, 0, length, data.data());
    return applyWavetableBank(data.data()) ? 0 : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_resetWavetableBank(JNIEnv* /* env */,
                                                           jobject /* thiz */) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->clearWavetable();
        return 0;
    }
    if (engine != nullptr) {
        engine->resetWavetableBank();
    } else {
        std::memcpy(Waveforms, FactoryWaveforms, sizeof(Waveforms));
    }
    hasCustomBank = false;
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setTet(JNIEnv* /* env */,
                                               jobject /* thiz */,
                                               jint new_tet) {
    if (activeYawnEngine() != nullptr) {
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleSetTet(new_tet);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setTune(JNIEnv* /* env */,
                                                jobject /* thiz */,
                                                jfloat new_tune) {
    if (activeYawnEngine() != nullptr) {
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleSetTune(new_tune);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setOctaveFactor(JNIEnv* /* env */,
                                                        jobject /* thiz */,
                                                        jfloat factor) {
    if (activeYawnEngine() != nullptr) {
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleSetOctaveFactor(factor);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setPlay(JNIEnv* /* env */,
                                                jobject /* thiz */,
                                                jboolean flag) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->setPlay(flag);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->SetPlay(flag);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setRecord(JNIEnv* /* env */,
                                                  jobject /* thiz */,
                                                  jboolean flag) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->setRecord(flag);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->SetRecord(flag);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendMidiBend(JNIEnv* /* env */,
                                                     jobject /* thiz */,
                                                     jint channel, jint low,
                                                     jint high) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->pitchBend(channel, low, high);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handlePitchBend((int)channel, (uint8_t)low, (uint8_t)high);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendMidiChangeNote(JNIEnv* /* env */,
                                                           jobject /* thiz */,
                                                           jint channel,
                                                           jint oldnote,
                                                           jint newnote,
                                                           jint velocity) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->noteOff(channel, oldnote, 0);
        gYawnEngine->noteOn(channel, newnote, velocity);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->handleNoteTransition(channel, oldnote, newnote, velocity);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setDelayLevel(JNIEnv* /* env */,
                                                      jobject /* thiz */,
                                                      jint level) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->setDelayLevel(level);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->SetDelayLevel(level / 255.0);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setDelayTime(JNIEnv* /* env */,
                                                     jobject /* thiz */,
                                                     jint time) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->setDelayTime((time * sr) / 255);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->SetDelayLength((time * sr) / 255);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setDelayFeedback(JNIEnv* /* env */,
                                                         jobject /* thiz */,
                                                         jint feedback) {
    if (activeYawnEngine() != nullptr) {
        gYawnEngine->setDelayFeedback(feedback);
        return 0;
    }
    if (engine == nullptr) {
        return -1;
    }
    engine->SetDelayFeedback(0.99 * ((float)feedback / 255.0));
    return 0;
}

static jstring yawnString(JNIEnv* env, const char* text) {
    return env->NewStringUTF(text != nullptr ? text : "");
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentCount(
    JNIEnv* /* env */, jobject /* thiz */) {
    return YawnEngine::kInstrumentCount;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentName(
    JNIEnv* env, jobject /* thiz */, jint index) {
    return yawnString(env, YawnEngine::instrumentName(index));
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentId(
    JNIEnv* env, jobject /* thiz */, jint index) {
    return yawnString(env, YawnEngine::instrumentId(index));
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamCount(
    JNIEnv* /* env */, jobject /* thiz */) {
    return gYawnEngine != nullptr ? gYawnEngine->instrumentParamCount() : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamName(
    JNIEnv* env, jobject /* thiz */, jint index) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->instrumentParamName(index)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamMin(
    JNIEnv* /* env */, jobject /* thiz */, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->instrumentParamMin(index) : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamMax(
    JNIEnv* /* env */, jobject /* thiz */, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->instrumentParamMax(index) : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamDefault(
    JNIEnv* /* env */, jobject /* thiz */, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->instrumentParamDefault(index)
                                  : 0.0f;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnInstrumentParamBoolean(
    JNIEnv* /* env */, jobject /* thiz */, jint index) {
    return gYawnEngine != nullptr && gYawnEngine->instrumentParamIsBoolean(index)
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamLabelCount(
    JNIEnv* /* env */, jobject /* thiz */, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->instrumentParamLabelCount(index)
               : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamLabel(
    JNIEnv* env, jobject /* thiz */, jint index, jint label) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->instrumentParamLabel(index, label)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnInstrumentParamValue(
    JNIEnv* /* env */, jobject /* thiz */, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->instrumentParamValue(index)
                                  : 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnInstrumentParam(
    JNIEnv* /* env */, jobject /* thiz */, jint index, jfloat value) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setInstrumentParam(index, value);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectCount(
    JNIEnv* /* env */, jobject /* thiz */) {
    return YawnEngine::kMidiEffectCount;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectName(
    JNIEnv* env, jobject /* thiz */, jint index) {
    return yawnString(env, YawnEngine::midiEffectName(index));
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectId(
    JNIEnv* env, jobject /* thiz */, jint index) {
    return yawnString(env, YawnEngine::midiEffectId(index));
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamCount(
    JNIEnv* /* env */, jobject /* thiz */, jint effect) {
    return gYawnEngine != nullptr ? gYawnEngine->midiEffectParamCount(effect) : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamName(
    JNIEnv* env, jobject /* thiz */, jint effect, jint index) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->midiEffectParamName(effect, index)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamMin(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->midiEffectParamMin(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamMax(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->midiEffectParamMax(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamDefault(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->midiEffectParamDefault(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnMidiEffectParamBoolean(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr &&
                   gYawnEngine->midiEffectParamIsBoolean(effect, index)
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamLabelCount(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->midiEffectParamLabelCount(effect, index)
               : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamLabel(
    JNIEnv* env, jobject /* thiz */, jint effect, jint index, jint label) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->midiEffectParamLabel(effect, index,
                                                                   label)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiEffectParamValue(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->midiEffectParamValue(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnMidiEffectParam(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index,
    jfloat value) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setMidiEffectParam(effect, index, value);
    }
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnMidiEffectBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint effect) {
    return gYawnEngine != nullptr && gYawnEngine->midiEffectBypassed(effect)
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnMidiEffectBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jboolean bypassed) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setMidiEffectBypassed(effect, bypassed == JNI_TRUE);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectCount(
    JNIEnv* /* env */, jobject /* thiz */) {
    return YawnEngine::kAudioEffectCount;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectName(
    JNIEnv* env, jobject /* thiz */, jint index) {
    return yawnString(env, YawnEngine::audioEffectName(index));
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectId(
    JNIEnv* env, jobject /* thiz */, jint index) {
    return yawnString(env, YawnEngine::audioEffectId(index));
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamCount(
    JNIEnv* /* env */, jobject /* thiz */, jint effect) {
    return gYawnEngine != nullptr ? gYawnEngine->audioEffectParamCount(effect) : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamName(
    JNIEnv* env, jobject /* thiz */, jint effect, jint index) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->audioEffectParamName(effect, index)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamMin(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->audioEffectParamMin(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamMax(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->audioEffectParamMax(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamDefault(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->audioEffectParamDefault(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnAudioEffectParamBoolean(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr &&
                   gYawnEngine->audioEffectParamIsBoolean(effect, index)
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamLabelCount(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->audioEffectParamLabelCount(effect, index)
               : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamLabel(
    JNIEnv* env, jobject /* thiz */, jint effect, jint index, jint label) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->audioEffectParamLabel(effect, index,
                                                                    label)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioEffectParamValue(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index) {
    return gYawnEngine != nullptr
               ? gYawnEngine->audioEffectParamValue(effect, index)
               : 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnAudioEffectParam(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jint index,
    jfloat value) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setAudioEffectParam(effect, index, value);
    }
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnAudioEffectBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint effect) {
    return gYawnEngine != nullptr && gYawnEngine->audioEffectBypassed(effect)
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnAudioEffectBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint effect, jboolean bypassed) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setAudioEffectBypassed(effect, bypassed == JNI_TRUE);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnTempo(JNIEnv* /* env */,
                                                     jobject /* thiz */,
                                                     jfloat bpm) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setTempo(bpm);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnMasterVolume(JNIEnv* /* env */,
                                                            jobject /* thiz */,
                                                            jfloat volume) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setMasterVolume(volume);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendYawnPolyPressure(
    JNIEnv* /* env */, jobject /* thiz */, jint channel, jint note,
    jint pressure) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->polyPressure(channel, note, pressure);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendYawnChannelPressure(
    JNIEnv* /* env */, jobject /* thiz */, jint channel, jint pressure) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->channelPressure(channel, pressure);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_sendYawnAllNotesOff(
    JNIEnv* /* env */, jobject /* thiz */) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->allNotesOff();
    }
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_loadYawnWavetable(
    JNIEnv* env, jobject /* thiz */, jstring path) {
    if (gYawnEngine == nullptr) {
        return yawnString(env, "YAWN engine is not initialized");
    }
    if (path == nullptr) {
        return yawnString(env, "No wavetable path supplied");
    }
    const char* chars = env->GetStringUTFChars(path, nullptr);
    std::string error;
    const bool ok = gYawnEngine->loadWavetable(chars, &error);
    env->ReleaseStringUTFChars(path, chars);
    if (!ok) {
        return yawnString(env, error.empty() ? "Could not load wavetable"
                                             : error.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_saveYawnWavetable(
    JNIEnv* env, jobject /* thiz */, jstring path) {
    if (gYawnEngine == nullptr) {
        return yawnString(env, "YAWN engine is not initialized");
    }
    if (path == nullptr) {
        return yawnString(env, "No wavetable path supplied");
    }
    const char* chars = env->GetStringUTFChars(path, nullptr);
    std::string error;
    const bool ok = gYawnEngine->saveWavetable(chars, &error);
    env->ReleaseStringUTFChars(path, chars);
    if (!ok) {
        return yawnString(env, error.empty() ? "Could not save wavetable"
                                             : error.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_resetYawnWavetable(
    JNIEnv* /* env */, jobject /* thiz */) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->clearWavetable();
    }
    return 0;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnTempo(JNIEnv* /* env */,
                                                     jobject /* thiz */) {
    return gYawnEngine != nullptr ? gYawnEngine->tempo() : 120.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMasterVolume(
    JNIEnv* /* env */, jobject /* thiz */) {
    return gYawnEngine != nullptr ? gYawnEngine->masterVolume() : 0.8f;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_saveYawnState(
    JNIEnv* env, jobject /* thiz */) {
    if (gYawnEngine == nullptr) {
        return yawnString(env, "");
    }
    return yawnString(env, gYawnEngine->saveState().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_restoreYawnState(
    JNIEnv* env, jobject /* thiz */, jstring state) {
    if (gYawnEngine == nullptr) {
        return yawnString(env, "YAWN engine is not initialized");
    }
    if (state == nullptr) {
        return yawnString(env, "No synth state supplied");
    }
    const char* chars = env->GetStringUTFChars(state, nullptr);
    std::string error;
    const bool ok = gYawnEngine->restoreState(chars, &error);
    env->ReleaseStringUTFChars(state, chars);
    if (!ok) {
        return yawnString(env, error.empty() ? "Could not restore synth state"
                                             : error.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_hasYawnCustomWavetable(
    JNIEnv* /* env */, jobject /* thiz */) {
    return gYawnEngine != nullptr && gYawnEngine->hasCustomWavetable()
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnWavetableFrame(
    JNIEnv* env, jobject /* thiz */, jfloat position) {
    jfloatArray result = env->NewFloatArray(256);
    if (result == nullptr) {
        return nullptr;
    }
    float frame[256] = {};
    if (gYawnEngine != nullptr) {
        gYawnEngine->wavetableFrame(position, frame, 256);
    }
    env->SetFloatArrayRegion(result, 0, 256, frame);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainSize(
    JNIEnv* /* env */, jobject /* thiz */) {
    return gYawnEngine != nullptr ? gYawnEngine->midiChainSize() : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainName(
    JNIEnv* env, jobject /* thiz */, jint slot) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->midiChainName(slot)
                               : "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainId(
    JNIEnv* env, jobject /* thiz */, jint slot) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->midiChainId(slot)
                               : "");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnMidiChainBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint slot) {
    return gYawnEngine != nullptr && gYawnEngine->midiChainBypassed(slot)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnMidiChainBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jboolean bypassed) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setMidiChainBypassed(slot, bypassed == JNI_TRUE);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_addYawnMidiEffect(
    JNIEnv* /* env */, jobject /* thiz */, jint type) {
    return gYawnEngine != nullptr ? gYawnEngine->addMidiEffect(type) : -1;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_removeYawnMidiEffect(
    JNIEnv* /* env */, jobject /* thiz */, jint slot) {
    return gYawnEngine != nullptr && gYawnEngine->removeMidiEffect(slot)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_moveYawnMidiEffect(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint delta) {
    return gYawnEngine != nullptr && gYawnEngine->moveMidiEffect(slot, delta)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamCount(
    JNIEnv* /* env */, jobject /* thiz */, jint slot) {
    return gYawnEngine != nullptr ? gYawnEngine->midiChainParamCount(slot) : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamName(
    JNIEnv* env, jobject /* thiz */, jint slot, jint index) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->midiChainParamName(slot, index)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamMin(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->midiChainParamMin(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamMax(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->midiChainParamMax(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamDefault(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->midiChainParamDefault(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnMidiChainParamBoolean(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr &&
                   gYawnEngine->midiChainParamIsBoolean(slot, index)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamLabelCount(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->midiChainParamLabelCount(slot, index) : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamLabel(
    JNIEnv* env, jobject /* thiz */, jint slot, jint index, jint label) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->midiChainParamLabel(slot, index, label)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnMidiChainParamValue(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->midiChainParamValue(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnMidiChainParam(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index,
    jfloat value) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setMidiChainParam(slot, index, value);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainSize(
    JNIEnv* /* env */, jobject /* thiz */) {
    return gYawnEngine != nullptr ? gYawnEngine->audioChainSize() : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainName(
    JNIEnv* env, jobject /* thiz */, jint slot) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->audioChainName(slot)
                               : "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainId(
    JNIEnv* env, jobject /* thiz */, jint slot) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->audioChainId(slot)
                               : "");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnAudioChainBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint slot) {
    return gYawnEngine != nullptr && gYawnEngine->audioChainBypassed(slot)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnAudioChainBypassed(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jboolean bypassed) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setAudioChainBypassed(slot, bypassed == JNI_TRUE);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_addYawnAudioEffect(
    JNIEnv* /* env */, jobject /* thiz */, jint type) {
    return gYawnEngine != nullptr ? gYawnEngine->addAudioEffect(type) : -1;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_removeYawnAudioEffect(
    JNIEnv* /* env */, jobject /* thiz */, jint slot) {
    return gYawnEngine != nullptr && gYawnEngine->removeAudioEffect(slot)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_moveYawnAudioEffect(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint delta) {
    return gYawnEngine != nullptr && gYawnEngine->moveAudioEffect(slot, delta)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamCount(
    JNIEnv* /* env */, jobject /* thiz */, jint slot) {
    return gYawnEngine != nullptr ? gYawnEngine->audioChainParamCount(slot) : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamName(
    JNIEnv* env, jobject /* thiz */, jint slot, jint index) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->audioChainParamName(slot, index)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamMin(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->audioChainParamMin(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamMax(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->audioChainParamMax(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamDefault(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->audioChainParamDefault(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_bokontep_wavesynth_SynthEngine_isYawnAudioChainParamBoolean(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr &&
                   gYawnEngine->audioChainParamIsBoolean(slot, index)
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamLabelCount(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->audioChainParamLabelCount(slot, index) : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamLabel(
    JNIEnv* env, jobject /* thiz */, jint slot, jint index, jint label) {
    return yawnString(env, gYawnEngine != nullptr
                               ? gYawnEngine->audioChainParamLabel(slot, index, label)
                               : "");
}

extern "C" JNIEXPORT jfloat JNICALL
Java_org_bokontep_wavesynth_SynthEngine_getYawnAudioChainParamValue(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index) {
    return gYawnEngine != nullptr ? gYawnEngine->audioChainParamValue(slot, index) : 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_bokontep_wavesynth_SynthEngine_setYawnAudioChainParam(
    JNIEnv* /* env */, jobject /* thiz */, jint slot, jint index,
    jfloat value) {
    if (gYawnEngine != nullptr) {
        gYawnEngine->setAudioChainParam(slot, index, value);
    }
    return 0;
}
