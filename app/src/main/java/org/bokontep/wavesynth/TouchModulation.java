package org.bokontep.wavesynth;

/** Normalizes touch gestures and scales the optional pitch-bend depth. */
public final class TouchModulation {
    private TouchModulation() {
    }

    public static float normalizedX(float x, float width) {
        if (width <= 0.0f) {
            return 0.0f;
        }
        return clamp((x / width) * 2.0f - 1.0f);
    }

    public static float normalizedY(float y, float height) {
        if (height <= 0.0f) {
            return 0.0f;
        }
        return clamp(1.0f - (y / height) * 2.0f);
    }

    public static float relativeX(float x, float startX, float width) {
        if (width <= 0.0f) {
            return 0.0f;
        }
        return clamp((x - startX) / (width * 0.5f));
    }

    public static float relativeY(float y, float startY, float height) {
        if (height <= 0.0f) {
            return 0.0f;
        }
        return clamp((startY - y) / (height * 0.5f));
    }

    public static double scaledBend(double bendSemitones, float amount) {
        return bendSemitones * clampAmount(amount);
    }

    private static float clamp(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static float clampAmount(float amount) {
        return Math.max(0.0f, Math.min(1.0f, amount));
    }
}
