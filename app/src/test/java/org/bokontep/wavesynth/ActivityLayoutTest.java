package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ActivityLayoutTest {
    @Test
    public void mainLayoutExposesCoreControls() {
        Context context = ApplicationProvider.getApplicationContext();
        Context themed = new android.view.ContextThemeWrapper(context,
                R.style.AppTheme);
        View root = LayoutInflater.from(themed).inflate(
                R.layout.activity_main, null);

        assertNotNull(root.findViewById(R.id.performanceArea));
        assertNotNull(root.findViewById(R.id.mscope));
        assertNotNull(root.findViewById(R.id.tabMod));
        assertNotNull(root.findViewById(R.id.modMatrixContainer));
        assertNotNull(root.findViewById(R.id.sensorIndicator));
        assertNotNull(root.findViewById(R.id.editScaleButton));
        assertNotNull(root.findViewById(R.id.registerCountSeekBar));
        assertNotNull(root.findViewById(R.id.registerUnitSpinner));
        assertNotNull(root.findViewById(R.id.registerPresetSpinner));
        assertNotNull(root.findViewById(R.id.registerControls));

        SeekBar pitchBend = root.findViewById(R.id.pitchBendSeekBar);
        assertNotNull(pitchBend);
        assertEquals(0, pitchBend.getProgress());
        assertEquals(100, pitchBend.getMax());
        TextView label = root.findViewById(R.id.pitchBendText);
        assertTrue(label.getText().toString().contains("Pitch bend: 0%"));
    }
}
