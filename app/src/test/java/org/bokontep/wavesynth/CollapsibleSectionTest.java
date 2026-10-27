package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CollapsibleSectionTest {
    @Test
    public void headerClickTogglesContentAndIndicator() {
        Context context = ApplicationProvider.getApplicationContext();
        CollapsibleSection section = new CollapsibleSection(context, "Filters", true);

        assertTrue(section.isExpanded());
        assertEquals(View.VISIBLE, section.content().getVisibility());
        TextView header = (TextView) section.root().getChildAt(0);
        assertTrue(header.getText().toString().startsWith("▾ Filters"));

        assertTrue(header.performClick());
        assertFalse(section.isExpanded());
        assertEquals(View.GONE, section.content().getVisibility());
        assertTrue(header.getText().toString().startsWith("▸ Filters"));

        assertTrue(header.performClick());
        assertTrue(section.isExpanded());
        assertEquals(View.VISIBLE, section.content().getVisibility());
    }
}
