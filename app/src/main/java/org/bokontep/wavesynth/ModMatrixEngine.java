package org.bokontep.wavesynth;

/** Engine surface used by the modulation matrix UI. */
public interface ModMatrixEngine {
    int ENGINE_LEGACY = 0;
    int ENGINE_YAWN_WAVETABLE = 1;
    int ENGINE_YAWN_SUBTRACTIVE = 2;
    int ENGINE_YAWN_KARPLUS = 3;

    int getEngineMode();
    int getYawnAudioChainSize();
    String getYawnAudioChainName(int slot);
    int getYawnAudioChainParamCount(int slot);
    boolean isYawnAudioChainParamBoolean(int slot, int param);
    int getYawnAudioChainParamLabelCount(int slot, int param);
    String getYawnAudioChainParamName(int slot, int param);
    float getYawnAudioChainParamMin(int slot, int param);
    float getYawnAudioChainParamMax(int slot, int param);

    int getYawnInstrumentParamCount();
    String getYawnInstrumentParamName(int index);
    float getYawnInstrumentParamMin(int index);
    float getYawnInstrumentParamMax(int index);

    int setYawnModSource(int source, float value);
    int setYawnModSlot(int slot, boolean enabled, int source,
                       int targetType, int targetSlot, int targetParam,
                       float amount);
}
