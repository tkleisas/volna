package org.bokontep.wavesynth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WavetableStoreTest {
    private WavetableStore store;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        store = new WavetableStore(context);
        store.clear();
    }

    @Test
    public void saveLoadAndClearRoundTrip() throws Exception {
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        store.save(data, "Test table", "test.vwt");

        assertTrue(store.hasStoredWavetable());
        assertEquals("Test table", store.displayName());
        assertEquals("test.vwt", store.sourceName());
        assertArrayEquals(data, store.load());
        assertTrue(store.filePath().endsWith("custom-wavetable.vwt"));

        store.clear();
        assertFalse(store.hasStoredWavetable());
        assertNull(store.load());
        assertEquals("", store.displayName());
        assertEquals("", store.sourceName());
    }

    @Test
    public void saveWithNullSourceClearsSourceName() throws Exception {
        store.save(new byte[] {9}, "Table", "old.vwt");
        store.save(new byte[] {10}, "Table 2", null);

        assertEquals("Table 2", store.displayName());
        assertEquals("", store.sourceName());
    }
}
