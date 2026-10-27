package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpectrumAnalyzerTest {
    private static final int SAMPLE_RATE = 48000;

    @Test
    public void sinePeaksNearOneKilohertz() {
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer();
        float[] samples = new float[1024];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(
                    2.0 * Math.PI * 1000.0 * i / SAMPLE_RATE);
        }

        float[] bars = new float[SpectrumAnalyzer.BAR_COUNT];
        for (int frame = 0; frame < 12; frame++) {
            analyzer.analyze(samples, SAMPLE_RATE, bars);
        }

        int best = 0;
        for (int i = 1; i < bars.length; i++) {
            if (bars[i] > bars[best]) {
                best = i;
            }
        }
        float frequency = SpectrumAnalyzer.frequencyForBar(best, SAMPLE_RATE);
        assertEquals(1000.0f, frequency, 150.0f);
    }

    @Test
    public void analyzeRequiresAFullBarBuffer() {
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer();
        float[] samples = new float[1024];
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(samples, SAMPLE_RATE, null));
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze(samples, SAMPLE_RATE,
                        new float[SpectrumAnalyzer.BAR_COUNT - 1]));
    }

    @Test
    public void invalidSampleRateFallsBackAndNyquistIsClamped() {
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer();
        float[] bars = new float[SpectrumAnalyzer.BAR_COUNT];
        analyzer.analyze(new float[1024], 0, bars);
        for (float bar : bars) {
            assertTrue(bar >= 0.0f && bar <= 1.0f);
        }
        assertTrue(SpectrumAnalyzer.frequencyForBar(
                SpectrumAnalyzer.BAR_COUNT - 1, 8000) <= 4000.0f);
        assertTrue(SpectrumAnalyzer.frequencyForBar(0, 8000) >= 30.0f);
    }

    @Test
    public void silenceProducesNoEnergy() {
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer();
        float[] samples = new float[1024];
        float[] bars = new float[SpectrumAnalyzer.BAR_COUNT];
        for (int frame = 0; frame < 4; frame++) {
            analyzer.analyze(samples, SAMPLE_RATE, bars);
        }
        for (float bar : bars) {
            assertTrue(bar >= 0.0f);
            assertTrue(bar < 0.01f);
        }
    }

    @Test
    public void barsCoverTheAudibleRange() {
        float first = SpectrumAnalyzer.frequencyForBar(0, SAMPLE_RATE);
        float last = SpectrumAnalyzer.frequencyForBar(
                SpectrumAnalyzer.BAR_COUNT - 1, SAMPLE_RATE);
        assertTrue(first >= 30.0f);
        assertTrue(last <= 16000.0f);
        assertTrue(last > first);
    }
}
