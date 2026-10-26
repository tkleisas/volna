package org.bokontep.wavesynth;

/** Maps normalized touch pressure/contact-size hints to MIDI values. */
public final class TouchExpression {
    public static final int UNSUPPORTED_VELOCITY = 100;
    private static final float VELOCITY_CURVE = 0.7f;

    private TouchExpression() {
    }

    public static boolean isSupported(float expression) {
        return expression >= 0.0f;
    }

    public static int velocity(float expression) {
        if (!isSupported(expression)) {
            return UNSUPPORTED_VELOCITY;
        }
        final float curved = (float) Math.pow(clamp01(expression), VELOCITY_CURVE);
        return clamp(1 + Math.round(126.0f * curved), 1, 127);
    }

    public static int midiPressure(float expression) {
        if (!isSupported(expression)) {
            return -1;
        }
        return clamp(Math.round(clamp01(expression) * 127.0f), 0, 127);
    }

    public static float smooth(float previous, float current) {
        if (!isSupported(current)) {
            return previous;
        }
        return previous * 0.7f + clamp01(current) * 0.3f;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
