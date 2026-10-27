package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppPreferencesTest {
    @Test
    public void scalarValuesRoundTripAcrossInstances() {
        Context context = ApplicationProvider.getApplicationContext();
        AppPreferences prefs = new AppPreferences(context);
        prefs.writeInt("ui_test_int", 42);
        prefs.writeFloat("ui_test_float", 0.75f);
        prefs.writeString("ui_test_string", "volna");

        AppPreferences reloaded = new AppPreferences(context);
        assertEquals(42, reloaded.readInt("ui_test_int", -1));
        assertEquals(0.75f, reloaded.readFloat("ui_test_float", 0.0f), 0.0f);
        assertEquals("volna", reloaded.readString("ui_test_string", ""));
    }

    @Test
    public void arraysRoundTripAsSets() {
        Context context = ApplicationProvider.getApplicationContext();
        AppPreferences prefs = new AppPreferences(context);
        prefs.writeIntArray("ui_test_ints", new int[] {1, 4, 9});
        prefs.writeFloatArray("ui_test_floats", new float[] {0.25f, 0.5f});

        int[] ints = new AppPreferences(context).readIntArray(
                "ui_test_ints", new int[0]);
        float[] floats = new AppPreferences(context).readFloatArray(
                "ui_test_floats", new float[0]);
        boolean hasFour = false;
        for (int value : ints) {
            hasFour |= value == 4;
        }
        boolean hasHalf = false;
        for (float value : floats) {
            hasHalf |= Math.abs(value - 0.5f) < 0.0001f;
        }
        assertTrue(hasFour);
        assertTrue(hasHalf);
    }
}
