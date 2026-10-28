package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;

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
}
