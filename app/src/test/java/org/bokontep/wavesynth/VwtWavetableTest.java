package org.bokontep.wavesynth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class VwtWavetableTest {
    private static final float EPSILON = 1.0e-5f;

    @Test
    public void mapsSingleFrameIntoEverySlot() throws Exception {
        float[] frame = new float[8];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = (float) i / frame.length;
        }

        VwtWavetable table = VwtWavetable.parse(
                makeVwt(2, 8, new float[][] {frame}, null, "single"));
        float[] bank = table.toVolnaBank(4, 8, 0.0f);

        assertEquals(32, bank.length);
        for (int slot = 1; slot < 4; slot++) {
            for (int i = 0; i < 8; i++) {
                assertEquals(bank[i], bank[slot * 8 + i], EPSILON);
            }
        }
    }

    @Test
    public void blendsFramesUsingUniformPositions() throws Exception {
        float[][] frames = {constantFrame(8, 0.0f), constantFrame(8, 1.0f)};
        VwtWavetable table = VwtWavetable.parse(
                makeVwt(2, 8, frames, null, "blend"));
        float[] bank = table.toVolnaBank(3, 8, 0.0f);

        assertEquals(0.0f, bank[0], EPSILON);
        assertEquals(0.5f, bank[8], EPSILON);
        assertEquals(1.0f, bank[16], EPSILON);
    }

    @Test
    public void usesKeyframePositions() throws Exception {
        float[][] frames = {
                constantFrame(8, 0.0f),
                constantFrame(8, 1.0f),
                constantFrame(8, 0.0f),
        };
        VwtWavetable table = VwtWavetable.parse(
                makeVwt(2, 8, frames,
                        new double[] {0.0, 0.75, 1.0}, "keyframes"));
        float[] bank = table.toVolnaBank(5, 8, 0.0f);

        assertEquals(0.0f, bank[0], EPSILON);
        assertEquals(1.0f / 3.0f, bank[8], EPSILON);
        assertEquals(2.0f / 3.0f, bank[16], EPSILON);
        assertEquals(1.0f, bank[24], EPSILON);
        assertEquals(0.0f, bank[32], EPSILON);
    }

    @Test
    public void fallsBackToUniformPositionsWhenKeyframesAreInvalid() throws Exception {
        float[][] frames = {constantFrame(4, 0.0f), constantFrame(4, 1.0f)};
        VwtWavetable table = VwtWavetable.parse(
                makeVwt(2, 4, frames,
                        new double[] {0.75, 0.25}, "bad keyframes"));
        float[] bank = table.toVolnaBank(3, 4, 0.0f);

        assertEquals(0.0f, bank[0], EPSILON);
        assertEquals(0.5f, bank[4], EPSILON);
        assertEquals(1.0f, bank[8], EPSILON);
    }

    @Test
    public void resamplesFramesToRequestedLength() throws Exception {
        float[] ramp = new float[1024];
        for (int i = 0; i < ramp.length; i++) {
            ramp[i] = -1.0f + 2.0f * i / (ramp.length - 1);
        }
        VwtWavetable table = VwtWavetable.parse(
                makeVwt(2, 1024, new float[][] {ramp}, null, "ramp"));
        float[] bank = table.toVolnaBank(1, 256, 0.0f);

        assertEquals(256, bank.length);
        assertTrue(bank[0] < -0.99f);
        assertTrue(bank[1] > bank[0]);
        assertTrue(bank[255] > 0.99f);
    }

    @Test
    public void normalizesBankPeak() throws Exception {
        float[][] frames = {constantFrame(16, 0.25f)};
        VwtWavetable table = VwtWavetable.parse(
                makeVwt(2, 16, frames, null, "quiet"));
        float[] bank = table.toVolnaBank(2, 16, 0.8f);

        assertEquals(0.8f, bank[0], EPSILON);
    }

    @Test
    public void acceptsVersionOne() throws Exception {
        VwtWavetable table = VwtWavetable.parse(
                makeVwt(1, 4, new float[][] {constantFrame(4, 0.5f)}, null, "v1"));
        assertEquals(1, table.version);
        assertEquals(1, table.frameCount);
        assertArrayEquals(new double[] {0.0}, table.positions, 0.0);
    }

    @Test
    public void rejectsWrongFormat() throws Exception {
        JSONObject root = validRoot(4, 1, constantFrame(4, 0.0f));
        root.put("format", "something-else");

        VwtWavetable.FormatException error = assertThrows(
                VwtWavetable.FormatException.class,
                () -> VwtWavetable.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("VWaveEditor"));
    }

    @Test
    public void rejectsDataSizeMismatch() throws Exception {
        JSONObject root = validRoot(8, 2, constantFrame(8, 0.0f));
        root.put("data", Base64.getEncoder().encodeToString(new byte[8]));

        assertThrows(VwtWavetable.FormatException.class,
                () -> VwtWavetable.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void rejectsInvalidBase64() throws Exception {
        JSONObject root = validRoot(4, 1, constantFrame(4, 0.0f));
        root.put("data", "not base64!");

        assertThrows(VwtWavetable.FormatException.class,
                () -> VwtWavetable.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void rejectsOversizedDimensions() throws Exception {
        JSONObject root = validRoot(4, 1, constantFrame(4, 0.0f));
        root.put("frameSize", 1024);
        root.put("frameCount", VwtWavetable.MAX_FRAMES + 1);

        assertThrows(VwtWavetable.FormatException.class,
                () -> VwtWavetable.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void keepsPositionsForSingleFrame() throws Exception {
        VwtWavetable table = VwtWavetable.parse(
                makeVwt(2, 4, new float[][] {constantFrame(4, 0.0f)}, null, "one"));
        assertNotNull(table.positions);
        assertEquals(1, table.positions.length);
        assertEquals(0.0, table.positions[0], 0.0);
    }

    private static float[] constantFrame(int size, float value) {
        float[] frame = new float[size];
        for (int i = 0; i < size; i++) {
            frame[i] = value;
        }
        return frame;
    }

    private static JSONObject validRoot(int frameSize, int frameCount, float[] firstFrame)
            throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "vwave-wavetable");
        root.put("version", 2);
        root.put("frameSize", frameSize);
        root.put("frameCount", frameCount);
        root.put("name", "test");
        ByteBuffer buffer = ByteBuffer.allocate(frameSize * frameCount * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frameCount; frame++) {
            for (float value : firstFrame) {
                buffer.putFloat(value);
            }
        }
        root.put("data", Base64.getEncoder().encodeToString(buffer.array()));
        return root;
    }

    private static byte[] makeVwt(int version, int frameSize, float[][] frames,
                                  double[] positions, String name) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "vwave-wavetable");
        root.put("version", version);
        root.put("frameSize", frameSize);
        root.put("frameCount", frames.length);
        root.put("sampleRate", 48000);
        root.put("name", name);

        ByteBuffer buffer = ByteBuffer.allocate(frameSize * frames.length * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float[] frame : frames) {
            for (float value : frame) {
                buffer.putFloat(value);
            }
        }
        root.put("data", Base64.getEncoder().encodeToString(buffer.array()));

        if (positions != null) {
            JSONArray array = new JSONArray();
            for (double position : positions) {
                array.put(position);
            }
            root.put("keyframePositions", array);
        }
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }
}
