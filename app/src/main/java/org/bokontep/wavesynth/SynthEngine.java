package org.bokontep.wavesynth;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

public class SynthEngine {
    public static final int ENGINE_LEGACY = 0;
    public static final int ENGINE_YAWN_WAVETABLE = 1;
    public static final int ENGINE_YAWN_SUBTRACTIVE = 2;

    static {
        System.loadLibrary("wavesynth-lib");
    }
    private int sampleRate;
    private int engineMode = ENGINE_LEGACY;
    public SynthEngine(Context context,int sampleRate)
    {
        this.context = context;
        this.setSampleRate(sampleRate);
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public void initAudio()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            AudioManager myAudioMgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            String sampleRateStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            int defaultSampleRate = Integer.parseInt(sampleRateStr);
            String framesPerBurstStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            int defaultFramesPerBurst = Integer.parseInt(framesPerBurstStr);

            setVAEngineDefaultStreamValues(defaultSampleRate, defaultFramesPerBurst);
            this.sampleRate = defaultSampleRate;
            if (engineMode == ENGINE_LEGACY) {
                initVAEngine(defaultSampleRate);
            } else {
                initYawnEngine(defaultSampleRate, engineMode - 1);
            }



        }
    }
    public void initSynthParameters()
    {
        sendMidiCC(0,18, getOsc1Attack());
        sendMidiCC(0,19, getOsc1Decay());
        sendMidiCC(0,20, getOsc1Sustain());
        sendMidiCC(0,21, getOsc1Release());
        sendMidiCC(0,22, getOsc2Attack());
        sendMidiCC(0,23, getOsc2Decay());
        sendMidiCC(0,24, getOsc2Sustain());
        sendMidiCC(0,25, getOsc2Release());

    }
    private Context context;
    private int osc1Volume = 127;
    private int osc1Attack = 10;
    private int osc1Decay = 0;
    private int osc1Sustain = 127;
    private int osc1Release = 0;
    private int osc2Volume = 127;
    private int osc2Attack = 10;
    private int osc2Decay = 0;
    private int osc2Sustain = 127;
    private int osc2Release =0;
    public native int initVAEngine(int sampleRate);
    public native int setVAEngineDefaultStreamValues(int sampleRate, int framesPerBurst);
    public native int sendMidiCC(int channel, int cc, int data);
    public native int sendMidiNoteOn(int channel, int note, int velocity );
    public native int sendMidiNoteOff(int channel, int note, int velocity );
    public native int sendMidiBend(int channel, int low, int high);
    public native int sendMidiNoteSpread(int channel, int note, int spread);
    public native int sendMidiChangeNote(int channel, int oldnote, int newnote, int velocity);
    public native int sendMidiNotePitch(int channel, int note, float bendSemitones);
    public native float getDspLoad();
    public native float getDspPeak();
    public native int resetDspPeak();
    public native int selectWaveform(int channel, int osc, int note,  int wave);
    public native float[] getWaveform();
    public native float[] getWavetable(int index);
    public native int setWavetable(int index, float[] wavetable);
    public native int setWavetableBank(float[] bank);
    public native int resetWavetableBank();
    public native int setTet(int newTet);
    public native int setTune(float newTune);
    public native int setOctaveFactor(float factor);
    public native int setRecord(boolean flag);
    public native int setPlay(boolean flag);
    public native int setSafetyLimiter(boolean enabled);
    public native int setPolyphonyLeveling(boolean enabled);
    public native float getLoopRecordProgress();
    public native float getLoopPlayProgress();
    public native boolean isLoopRecording();
    public native boolean isLoopPlaying();
    public native boolean hasLoop();
    public native int clearLoop();
    public native int setDelayLevel(int level);
    public native int setDelayTime(int time);
    public native int setDelayFeedback(int feedback);
    public native int pauseAudio();
    public native int resumeAudio();
    public native int shutdownVAEngine();

    public native int initYawnEngine(int sampleRate, int instrumentIndex);
    public native int switchEngine(int mode, int instrumentIndex);
    public native int selectYawnInstrument(int instrumentIndex);

    public native int getYawnInstrumentCount();
    public native String getYawnInstrumentName(int index);
    public native String getYawnInstrumentId(int index);
    public native int getYawnInstrumentParamCount();
    public native String getYawnInstrumentParamName(int index);
    public native float getYawnInstrumentParamMin(int index);
    public native float getYawnInstrumentParamMax(int index);
    public native float getYawnInstrumentParamDefault(int index);
    public native boolean isYawnInstrumentParamBoolean(int index);
    public native int getYawnInstrumentParamLabelCount(int index);
    public native String getYawnInstrumentParamLabel(int index, int label);
    public native float getYawnInstrumentParamValue(int index);
    public native int setYawnInstrumentParam(int index, float value);

    public native int getYawnMidiEffectCount();
    public native String getYawnMidiEffectName(int index);
    public native String getYawnMidiEffectId(int index);
    public native int getYawnMidiEffectParamCount(int effect);
    public native String getYawnMidiEffectParamName(int effect, int index);
    public native float getYawnMidiEffectParamMin(int effect, int index);
    public native float getYawnMidiEffectParamMax(int effect, int index);
    public native float getYawnMidiEffectParamDefault(int effect, int index);
    public native boolean isYawnMidiEffectParamBoolean(int effect, int index);
    public native int getYawnMidiEffectParamLabelCount(int effect, int index);
    public native String getYawnMidiEffectParamLabel(int effect, int index, int label);
    public native float getYawnMidiEffectParamValue(int effect, int index);
    public native int setYawnMidiEffectParam(int effect, int index, float value);
    public native boolean isYawnMidiEffectBypassed(int effect);
    public native int setYawnMidiEffectBypassed(int effect, boolean bypassed);

    public native int getYawnAudioEffectCount();
    public native String getYawnAudioEffectName(int index);
    public native String getYawnAudioEffectId(int index);
    public native int getYawnAudioEffectParamCount(int effect);
    public native String getYawnAudioEffectParamName(int effect, int index);
    public native float getYawnAudioEffectParamMin(int effect, int index);
    public native float getYawnAudioEffectParamMax(int effect, int index);
    public native float getYawnAudioEffectParamDefault(int effect, int index);
    public native boolean isYawnAudioEffectParamBoolean(int effect, int index);
    public native int getYawnAudioEffectParamLabelCount(int effect, int index);
    public native String getYawnAudioEffectParamLabel(int effect, int index, int label);
    public native float getYawnAudioEffectParamValue(int effect, int index);
    public native int setYawnAudioEffectParam(int effect, int index, float value);
    public native boolean isYawnAudioEffectBypassed(int effect);
    public native int setYawnAudioEffectBypassed(int effect, boolean bypassed);

    public native int getYawnMidiChainSize();
    public native String getYawnMidiChainName(int slot);
    public native String getYawnMidiChainId(int slot);
    public native boolean isYawnMidiChainBypassed(int slot);
    public native int setYawnMidiChainBypassed(int slot, boolean bypassed);
    public native int addYawnMidiEffect(int type);
    public native boolean removeYawnMidiEffect(int slot);
    public native boolean moveYawnMidiEffect(int slot, int delta);
    public native int getYawnMidiChainParamCount(int slot);
    public native String getYawnMidiChainParamName(int slot, int index);
    public native float getYawnMidiChainParamMin(int slot, int index);
    public native float getYawnMidiChainParamMax(int slot, int index);
    public native float getYawnMidiChainParamDefault(int slot, int index);
    public native boolean isYawnMidiChainParamBoolean(int slot, int index);
    public native int getYawnMidiChainParamLabelCount(int slot, int index);
    public native String getYawnMidiChainParamLabel(int slot, int index, int label);
    public native float getYawnMidiChainParamValue(int slot, int index);
    public native int setYawnMidiChainParam(int slot, int index, float value);

    public native int getYawnAudioChainSize();
    public native String getYawnAudioChainName(int slot);
    public native String getYawnAudioChainId(int slot);
    public native boolean isYawnAudioChainBypassed(int slot);
    public native int setYawnAudioChainBypassed(int slot, boolean bypassed);
    public native int addYawnAudioEffect(int type);
    public native boolean removeYawnAudioEffect(int slot);
    public native boolean moveYawnAudioEffect(int slot, int delta);
    public native int getYawnAudioChainParamCount(int slot);
    public native String getYawnAudioChainParamName(int slot, int index);
    public native float getYawnAudioChainParamMin(int slot, int index);
    public native float getYawnAudioChainParamMax(int slot, int index);
    public native float getYawnAudioChainParamDefault(int slot, int index);
    public native boolean isYawnAudioChainParamBoolean(int slot, int index);
    public native int getYawnAudioChainParamLabelCount(int slot, int index);
    public native String getYawnAudioChainParamLabel(int slot, int index, int label);
    public native float getYawnAudioChainParamValue(int slot, int index);
    public native int setYawnAudioChainParam(int slot, int index, float value);

    public native int setYawnTempo(float bpm);
    public native int setYawnModSource(int source, float value);
    public native int setYawnModSlot(int slot, boolean enabled, int source,
                                     int targetType, int targetSlot,
                                     int targetParam, float amount);
    public native int setYawnMasterVolume(float volume);
    public native int sendYawnPolyPressure(int channel, int note, int pressure);
    public native int sendYawnChannelPressure(int channel, int pressure);
    public native int sendYawnAllNotesOff();
    public native String loadYawnWavetable(String path);
    public native String saveYawnWavetable(String path);
    public native int resetYawnWavetable();
    public native boolean hasYawnCustomWavetable();
    public native float getYawnTempo();
    public native float getYawnMasterVolume();
    public native String saveYawnState();
    public native String restoreYawnState(String state);
    public native float[] getYawnWavetableFrame(float position);

    public int getEngineMode() {
        return engineMode;
    }

    public void setEngineMode(int mode) {
        engineMode = mode;
    }

    public int switchEngine(int mode) {
        engineMode = mode;
        if (mode == ENGINE_LEGACY) {
            return switchEngine(ENGINE_LEGACY, 0);
        }
        return switchEngine(1, mode - 1);
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getOsc1Volume() {
        return osc1Volume;
    }

    public void setOsc1Volume(int osc1Volume) {
        this.osc1Volume = osc1Volume;
    }

    public int getOsc1Attack() {
        return osc1Attack;
    }

    public void setOsc1Attack(int osc1Attack) {
        this.osc1Attack = osc1Attack;
    }

    public int getOsc1Decay() {
        return osc1Decay;
    }

    public void setOsc1Decay(int osc1Decay) {
        this.osc1Decay = osc1Decay;
    }

    public int getOsc1Sustain() {
        return osc1Sustain;
    }

    public void setOsc1Sustain(int osc1Sustain) {
        this.osc1Sustain = osc1Sustain;
    }

    public int getOsc1Release() {
        return osc1Release;
    }

    public void setOsc1Release(int osc1Release) {
        this.osc1Release = osc1Release;
    }

    public int getOsc2Volume() {
        return osc2Volume;
    }

    public void setOsc2Volume(int osc2Volume) {
        this.osc2Volume = osc2Volume;
    }

    public int getOsc2Attack() {
        return osc2Attack;
    }

    public void setOsc2Attack(int osc2Attack) {
        this.osc2Attack = osc2Attack;
    }

    public int getOsc2Decay() {
        return osc2Decay;
    }

    public void setOsc2Decay(int osc2Decay) {
        this.osc2Decay = osc2Decay;
    }

    public int getOsc2Sustain() {
        return osc2Sustain;
    }

    public void setOsc2Sustain(int osc2Sustain) {
        this.osc2Sustain = osc2Sustain;
    }

    public int getOsc2Release() {
        return osc2Release;
    }

    public void setOsc2Release(int osc2Release) {
        this.osc2Release = osc2Release;
    }
}
