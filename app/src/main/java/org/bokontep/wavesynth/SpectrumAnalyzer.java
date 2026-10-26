package org.bokontep.wavesynth;

/**
 * Small real-input FFT used by the scope. It intentionally keeps a fixed
 * number of log-spaced bars so drawing stays cheap and allocation-free.
 */
public final class SpectrumAnalyzer {
    public static final int BAR_COUNT = 64;

    private static final int FFT_SIZE = 1024;
    private static final float MIN_HZ = 30.0f;
    private static final float MAX_HZ = 16000.0f;
    private static final float DB_FLOOR = -80.0f;

    private final float[] window = new float[FFT_SIZE];
    private final float[] real = new float[FFT_SIZE];
    private final float[] imag = new float[FFT_SIZE];
    private final float[] cosTable = new float[FFT_SIZE / 2];
    private final float[] sinTable = new float[FFT_SIZE / 2];
    private final float[] smoothed = new float[BAR_COUNT];
    private final int[] binLow = new int[BAR_COUNT];
    private final int[] binHigh = new int[BAR_COUNT];
    private int mappedSampleRate = -1;

    public SpectrumAnalyzer() {
        for (int i = 0; i < FFT_SIZE; i++) {
            window[i] = 0.5f - 0.5f * (float) Math.cos(
                    2.0 * Math.PI * i / (FFT_SIZE - 1));
        }
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            double angle = -2.0 * Math.PI * i / FFT_SIZE;
            cosTable[i] = (float) Math.cos(angle);
            sinTable[i] = (float) Math.sin(angle);
        }
    }

    public void analyze(float[] samples, int sampleRate, float[] out) {
        if (out == null || out.length < BAR_COUNT) {
            throw new IllegalArgumentException("Output needs " + BAR_COUNT
                    + " bars");
        }
        if (sampleRate <= 0) {
            sampleRate = 48000;
        }
        updateBins(sampleRate);

        final int available = samples == null ? 0 : samples.length;
        final int start = Math.max(0, available - FFT_SIZE);
        for (int i = 0; i < FFT_SIZE; i++) {
            float sample = 0.0f;
            if (samples != null && start + i < available) {
                sample = samples[start + i];
            }
            real[i] = sample * window[i];
            imag[i] = 0.0f;
        }
        fft();

        final float scale = 4.0f / FFT_SIZE;
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            float max = 0.0f;
            for (int bin = binLow[bar]; bin <= binHigh[bar]; bin++) {
                float re = real[bin];
                float im = imag[bin];
                float magnitude = (float) Math.sqrt(re * re + im * im) * scale;
                if (magnitude > max) {
                    max = magnitude;
                }
            }
            float db = 20.0f * (float) Math.log10(max + 1.0e-7f);
            float value = (db - DB_FLOOR) / -DB_FLOOR;
            value = Math.max(0.0f, Math.min(1.0f, value));
            value = (float) Math.pow(value, 0.75);

            float previous = smoothed[bar];
            smoothed[bar] = value > previous
                    ? value
                    : previous * 0.82f + value * 0.18f;
            out[bar] = smoothed[bar];
        }
    }

    public static float frequencyForBar(int bar, int sampleRate) {
        float maxHz = Math.min(MAX_HZ, Math.max(1, sampleRate) * 0.5f);
        double logMin = Math.log(MIN_HZ);
        double logMax = Math.log(maxHz);
        double frequency = Math.exp(logMin
                + (logMax - logMin) * ((bar + 0.5) / BAR_COUNT));
        return (float) frequency;
    }

    private void updateBins(int sampleRate) {
        if (sampleRate == mappedSampleRate) {
            return;
        }
        mappedSampleRate = sampleRate;
        final float maxHz = Math.min(MAX_HZ, sampleRate * 0.5f);
        final double logMin = Math.log(MIN_HZ);
        final double logMax = Math.log(maxHz);
        final int maxBin = FFT_SIZE / 2 - 1;

        for (int bar = 0; bar < BAR_COUNT; bar++) {
            double f0 = Math.exp(logMin
                    + (logMax - logMin) * (bar / (double) BAR_COUNT));
            double f1 = Math.exp(logMin
                    + (logMax - logMin) * ((bar + 1) / (double) BAR_COUNT));
            int low = Math.max(0, (int) Math.floor(f0 * FFT_SIZE / sampleRate));
            int high = Math.max(low,
                    (int) Math.ceil(f1 * FFT_SIZE / sampleRate));
            binLow[bar] = Math.min(low, maxBin);
            binHigh[bar] = Math.min(high, maxBin);
        }
    }

    private void fft() {
        for (int i = 1, j = 0; i < FFT_SIZE; i++) {
            int bit = FFT_SIZE >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float tr = real[i];
                real[i] = real[j];
                real[j] = tr;
                float ti = imag[i];
                imag[i] = imag[j];
                imag[j] = ti;
            }
        }

        for (int len = 2; len <= FFT_SIZE; len <<= 1) {
            int half = len >> 1;
            int step = FFT_SIZE / len;
            for (int i = 0; i < FFT_SIZE; i += len) {
                for (int j = 0; j < half; j++) {
                    int k = j * step;
                    float wr = cosTable[k];
                    float wi = sinTable[k];
                    int a = i + j;
                    int b = a + half;
                    float tr = real[b] * wr - imag[b] * wi;
                    float ti = real[b] * wi + imag[b] * wr;
                    real[b] = real[a] - tr;
                    imag[b] = imag[a] - ti;
                    real[a] += tr;
                    imag[a] += ti;
                }
            }
        }
    }
}
