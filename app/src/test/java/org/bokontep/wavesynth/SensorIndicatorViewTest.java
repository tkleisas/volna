package org.bokontep.wavesynth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SensorIndicatorViewTest {
    @Test
    public void accessibilityDescriptionReflectsActiveSensorGroups() {
        Context context = ApplicationProvider.getApplicationContext();
        SensorIndicatorView view = new SensorIndicatorView(context, null);

        assertTrue(view.getContentDescription().toString()
                .contains("Motion sensors off"));

        view.setState(true, 1 | 4, 0.5f);
        String active = view.getContentDescription().toString();
        assertTrue(active.contains("active"));
        assertTrue(active.contains("accelerometer"));
        assertTrue(active.contains("magnetometer"));
        assertFalse(active.contains("gyroscope"));
        assertTrue(active.contains("value +0.50"));

        view.setState(true, 2, -5.0f);
        String clamped = view.getContentDescription().toString();
        assertTrue(clamped.contains("gyroscope"));
        assertTrue(clamped.contains("value -1.00"));
    }

    @Test
    public void inactiveStateHidesValues() {
        Context context = ApplicationProvider.getApplicationContext();
        SensorIndicatorView view = new SensorIndicatorView(context, null);
        view.setState(false, 7, 0.4f);
        String description = view.getContentDescription().toString();
        assertTrue(description.contains("off"));
        assertFalse(description.contains("value"));
    }
}
