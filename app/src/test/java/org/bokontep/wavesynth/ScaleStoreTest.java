package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScaleStoreTest {
    private AppPreferences prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = new AppPreferences(context);
        prefs.writeString(ScaleStore.PREF_KEY, "");
    }

    @Test
    public void customTet12ScalesSurviveReloadAndCanBeDeleted() {
        ScaleStore store = new ScaleStore(prefs);
        ScaleDefinition saved = store.saveCustom(null, "My scale",
                new int[] {0, 2, 5, 7, 9});

        ScaleStore reloaded = new ScaleStore(prefs);
        ScaleDefinition found = reloaded.find(saved.id, 12);
        assertNotNull(found);
        assertEquals("My scale", found.name);
        assertEquals(5, found.size());

        assertTrue(reloaded.deleteCustom(saved.id));
        assertFalse(new ScaleStore(prefs).customScales().stream()
                .anyMatch(scale -> scale.id.equals(saved.id)));
    }

    @Test
    public void scalesAreFilteredByTetAndFallBackToChromatic() {
        ScaleStore store = new ScaleStore(prefs);
        List<ScaleDefinition> tet24 = store.scalesForTet(24);
        assertTrue(tet24.stream().anyMatch(scale -> scale.id.equals("24:major")));
        assertTrue(tet24.stream().noneMatch(scale -> scale.tet != 24));

        List<ScaleDefinition> tet19 = store.scalesForTet(19);
        assertEquals(1, tet19.size());
        assertEquals(19, tet19.get(0).tet);
        assertTrue(tet19.get(0).isChromatic());
    }
}
