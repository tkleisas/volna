package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ApplicationIdTest {
    @Test
    public void buildUsesTheNewPlayApplicationId() {
        assertEquals("gr.tkleisas.volna", BuildConfig.APPLICATION_ID);
    }

    @Test
    public void buildUsesThePlayMinimumSdk() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(29, context.getApplicationInfo().minSdkVersion);
        assertEquals(36, context.getApplicationInfo().targetSdkVersion);
    }
}
