package org.bokontep.wavesynth;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Parser and mapper for VWaveEditor {@code .vwt} wavetable files.
 *
 * <p>The on-disk format is a JSON document with base64-encoded little-endian
 * float samples. Volna presents a fixed 256-slot morph bank, so imported
 * frames are resampled and blended into that layout here.</p>
 */
public final class VwtWavetable {
    public static final int MAX_FRAME_SIZE = 65536;
    public static final int MAX_FRAMES = 4096;
    public static final int MAX_TOTAL_SAMPLES = 8_000_000;
    public static final int MAX_FILE_BYTES = 32 * 1024 * 1024;

    public final String name;
    public final int version;
    public final int frameSize;
    public final int frameCount;
    public final int sampleRate;
    public final float[][] frames;
    public final double[] positions;

    private VwtWavetable(String name, int version, int frameSize, int frameCount,
                         int sampleRate, float[][] frames, double[] positions) {
        this.name = name;
        this.version = version;
        this.frameSize = frameSize;
        this.frameCount = frameCount;
        this.sampleRate = sampleRate;
        this.frames = frames;
        this.positions = positions;
    }

    public static VwtWavetable parse(byte[] encoded) throws FormatException {
        if (encoded == null || encoded.length == 0) {
            throw new FormatException("The file is empty");
        }
        if (encoded.length > MAX_FILE_BYTES) {
            throw new FormatException("The file is too large");
        }

        String text = new String(encoded, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        final JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException e) {
            throw new FormatException("Invalid JSON: " + e.getMessage());
        }

        if (!"vwave-wavetable".equals(root.optString("format", ""))) {
            throw new FormatException("Not a VWaveEditor wavetable");
        }

        final int version = root.optInt("version", 0);
        if (version != 1 && version != 2) {
            throw new FormatException("Unsupported wavetable version " + version);
        }

        final int frameSize = root.optInt("frameSize", 0);
        final int frameCount = root.optInt("frameCount", 0);
        if (frameSize <= 0 || frameSize > MAX_FRAME_SIZE) {
            throw new FormatException("Unsupported frame size " + frameSize);
        }
        if (frameCount <= 0 || frameCount > MAX_FRAMES) {
            throw new FormatException("Unsupported frame count " + frameCount);
        }
        final long totalSamples = (long) frameSize * (long) frameCount;
        if (totalSamples > MAX_TOTAL_SAMPLES) {
            throw new FormatException("Wavetable is too large");
        }

        final String dataText = root.optString("data", "");
        if (dataText.isEmpty()) {
            throw new FormatException("Wavetable has no sample data");
        }
        final byte[] bytes = decodeBase64(dataText);
        final long expectedBytes = totalSamples * 4L;
        if (bytes.length != expectedBytes) {
            throw new FormatException("Wavetable data size mismatch");
        }

        final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        final float[][] frames = new float[frameCount][frameSize];
        for (int frame = 0; frame < frameCount; frame++) {
            for (int sample = 0; sample < frameSize; sample++) {
                frames[frame][sample] = buffer.getFloat();
            }
        }

        double[] positions = null;
        if (version == 2 && root.has("keyframePositions")) {
            positions = parsePositions(root.optJSONArray("keyframePositions"), frameCount);
        }
        if (positions == null) {
            positions = new double[frameCount];
            for (int i = 0; i < frameCount; i++) {
                positions[i] = frameCount == 1 ? 0.0 : (double) i / (frameCount - 1);
            }
        }

        String name = root.optString("name", "").trim();
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        if (name.isEmpty()) {
            name = "Imported wavetable";
        }
        int sampleRate = root.optInt("sampleRate", 48000);
        sampleRate = Math.max(8000, Math.min(192000, sampleRate));

        return new VwtWavetable(name, version, frameSize, frameCount, sampleRate,
                frames, positions);
    }

    /**
     * Maps the imported frames into Volna's fixed-size morph bank.
     *
     * @param slots      number of table slots (256 in Volna)
     * @param frameLength samples per table (256 in Volna)
     * @param targetPeak peak amplitude to normalize to; pass 0 to keep levels
     */
    public float[] toVolnaBank(int slots, int frameLength, float targetPeak) {
        if (slots <= 0 || frameLength <= 0) {
            throw new IllegalArgumentException("Bank dimensions must be positive");
        }

        final float[][] resampled = new float[frameCount][frameLength];
        for (int frame = 0; frame < frameCount; frame++) {
            for (int sample = 0; sample < frameLength; sample++) {
                resampled[frame][sample] =
                        sampleFrame(frames[frame], (double) sample / frameLength);
            }
        }

        final float[] bank = new float[slots * frameLength];
        double peak = 0.0;
        for (int slot = 0; slot < slots; slot++) {
            final double position = slots == 1 ? 0.0 : (double) slot / (slots - 1);
            final int lower = lowerFrame(position);
            final int upper = upperFrame(position);
            final double span = positions[upper] - positions[lower];
            double mix = span > 0.0 ? (position - positions[lower]) / span : 0.0;
            mix = Math.max(0.0, Math.min(1.0, mix));

            final int base = slot * frameLength;
            for (int sample = 0; sample < frameLength; sample++) {
                final float a = resampled[lower][sample];
                float value = a;
                if (upper != lower) {
                    final float b = resampled[upper][sample];
                    value = (float) (a + (b - a) * mix);
                }
                bank[base + sample] = value;
                peak = Math.max(peak, Math.abs(value));
            }
        }

        if (targetPeak > 0.0f && peak > 1.0e-9) {
            final float scale = targetPeak / (float) peak;
            for (int i = 0; i < bank.length; i++) {
                bank[i] *= scale;
            }
        }
        return bank;
    }

    private int lowerFrame(double position) {
        if (position <= positions[0]) {
            return 0;
        }
        final int last = frameCount - 1;
        if (position >= positions[last]) {
            return last;
        }
        int low = 0;
        int high = last;
        while (low + 1 < high) {
            final int mid = (low + high) >>> 1;
            if (positions[mid] <= position) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private int upperFrame(double position) {
        if (position <= positions[0]) {
            return 0;
        }
        final int last = frameCount - 1;
        if (position >= positions[last]) {
            return last;
        }
        int low = 0;
        int high = last;
        while (low + 1 < high) {
            final int mid = (low + high) >>> 1;
            if (positions[mid] <= position) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    private static float sampleFrame(float[] frame, double phase) {
        if (frame.length == 1) {
            return frame[0];
        }
        double x = phase * frame.length;
        if (x < 0.0) {
            x = 0.0;
        }
        int index0 = (int) Math.floor(x);
        if (index0 >= frame.length) {
            index0 = frame.length - 1;
        }
        final int index1 = (index0 + 1) % frame.length;
        final double fraction = x - index0;
        return (float) (frame[index0] + (frame[index1] - frame[index0]) * fraction);
    }

    private static double[] parsePositions(JSONArray array, int frameCount) {
        if (array == null || array.length() != frameCount) {
            return null;
        }
        final double[] positions = new double[frameCount];
        double previous = -Double.MAX_VALUE;
        for (int i = 0; i < frameCount; i++) {
            final double value = array.optDouble(i, Double.NaN);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            if (value < previous) {
                return null;
            }
            positions[i] = value;
            previous = value;
        }
        return positions;
    }

    private static byte[] decodeBase64(String input) throws FormatException {
        final StringBuilder cleaned = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (!Character.isWhitespace(c)) {
                cleaned.append(c);
            }
        }
        final String text = cleaned.toString();
        if (text.isEmpty() || text.length() % 4 != 0) {
            throw new FormatException("Invalid base64 wavetable data");
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream(text.length() * 3 / 4);
        for (int i = 0; i < text.length(); i += 4) {
            final int c0 = decodeBase64Char(text.charAt(i));
            final int c1 = decodeBase64Char(text.charAt(i + 1));
            final int c2 = decodeBase64Char(text.charAt(i + 2));
            final int c3 = decodeBase64Char(text.charAt(i + 3));
            if (c0 < 0 || c1 < 0) {
                throw new FormatException("Invalid base64 wavetable data");
            }

            out.write((c0 << 2) | (c1 >> 4));
            if (c2 == Base64Padding) {
                if (c3 != Base64Padding || i + 4 != text.length()) {
                    throw new FormatException("Invalid base64 wavetable data");
                }
                break;
            }
            if (c2 < 0) {
                throw new FormatException("Invalid base64 wavetable data");
            }
            out.write(((c1 & 0x0F) << 4) | (c2 >> 2));
            if (c3 == Base64Padding) {
                if (i + 4 != text.length()) {
                    throw new FormatException("Invalid base64 wavetable data");
                }
                break;
            }
            if (c3 < 0) {
                throw new FormatException("Invalid base64 wavetable data");
            }
            out.write(((c2 & 0x03) << 6) | c3);
        }
        return out.toByteArray();
    }

    private static final int Base64Padding = -2;

    private static int decodeBase64Char(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == '+' || c == '-') {
            return 62;
        }
        if (c == '/' || c == '_') {
            return 63;
        }
        if (c == '=') {
            return Base64Padding;
        }
        return -1;
    }

    public static class FormatException extends Exception {
        public FormatException(String message) {
            super(message);
        }
    }
}
