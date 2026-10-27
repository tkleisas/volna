package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ModMatrixControllerTest {
    private static final String PREF_KEY = "modMatrixV1";

    private Activity activity;
    private AppPreferences prefs;
    private FakeEngine engine;
    private LinearLayout container;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        prefs = new AppPreferences(activity);
        prefs.writeString(PREF_KEY, "");

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        container = new LinearLayout(activity);
        container.setId(R.id.modMatrixContainer);
        root.addView(container);
        addView(root, R.id.addModButton, new Button(activity));
        addView(root, R.id.calibrateModButton, new Button(activity));
        addView(root, R.id.modStatusText, new TextView(activity));
        addView(root, R.id.sensorIndicator, new SensorIndicatorView(activity, null));
        addView(root, R.id.modSensorIndicator, new SensorIndicatorView(activity, null));
        activity.setContentView(root);

        engine = new FakeEngine();
        new ModMatrixController(activity, engine, prefs).attach();
    }

    @Test
    public void touchPitchRowIsPersistedAndSentToTheEngine() {
        View row = container.getChildAt(0);
        assertNotNull(row);
        List<Spinner> spinners = findViews(row, Spinner.class);
        assertEquals(2, spinners.size());
        Spinner source = spinners.get(0);
        Spinner target = spinners.get(1);

        selectByText(source, "Touch ΔX");
        selectByText(target, "Pitch");

        ToggleButton enable = findViews(row, ToggleButton.class).get(0);
        enable.setChecked(true);

        FakeSlot slot = engine.slots.get(0);
        assertNotNull(slot);
        assertTrue(slot.enabled);
        assertEquals(SensorModulation.TOUCH_DELTA_X, slot.source);
        assertEquals(3, slot.targetType);
        assertEquals(0, slot.targetSlot);
        assertEquals(0, slot.targetParam);

        String status = ((TextView) activity.findViewById(R.id.modStatusText))
                .getText().toString();
        assertTrue(status.contains("Touch ΔX"));

        ModMatrixController reloaded = new ModMatrixController(activity, engine, prefs);
        reloaded.attach();
        View restored = container.getChildAt(0);
        List<Spinner> restoredSpinners = findViews(restored, Spinner.class);
        assertEquals("Touch ΔX", restoredSpinners.get(0).getSelectedItem());
        assertEquals("Pitch", restoredSpinners.get(1).getSelectedItem());
        assertTrue(findViews(restored, ToggleButton.class).get(0).isChecked());
    }

    private void addView(LinearLayout root, int id, View view) {
        view.setId(id);
        root.addView(view);
    }

    private static <T extends View> List<T> findViews(View root, Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(root, type, result);
        return result;
    }

    private static <T extends View> void collect(View view, Class<T> type,
                                                 List<T> result) {
        if (type.isInstance(view)) {
            result.add(type.cast(view));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), type, result);
            }
        }
    }

    private static void selectByText(Spinner spinner, String text) {
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            if (text.equals(spinner.getAdapter().getItem(i))) {
                spinner.setSelection(i);
                return;
            }
        }
        throw new AssertionError("Spinner item not found: " + text);
    }

    private static final class FakeEngine implements ModMatrixEngine {
        final Map<Integer, FakeSlot> slots = new HashMap<>();

        @Override
        public int getEngineMode() {
            return ENGINE_YAWN_WAVETABLE;
        }

        @Override
        public int getYawnAudioChainSize() {
            return 0;
        }

        @Override
        public String getYawnAudioChainName(int slot) {
            return "";
        }

        @Override
        public int getYawnAudioChainParamCount(int slot) {
            return 0;
        }

        @Override
        public boolean isYawnAudioChainParamBoolean(int slot, int param) {
            return false;
        }

        @Override
        public int getYawnAudioChainParamLabelCount(int slot, int param) {
            return 0;
        }

        @Override
        public String getYawnAudioChainParamName(int slot, int param) {
            return "";
        }

        @Override
        public float getYawnAudioChainParamMin(int slot, int param) {
            return 0.0f;
        }

        @Override
        public float getYawnAudioChainParamMax(int slot, int param) {
            return 1.0f;
        }

        @Override
        public int getYawnInstrumentParamCount() {
            return 32;
        }

        @Override
        public String getYawnInstrumentParamName(int index) {
            return "Param " + index;
        }

        @Override
        public float getYawnInstrumentParamMin(int index) {
            return 0.0f;
        }

        @Override
        public float getYawnInstrumentParamMax(int index) {
            return 1.0f;
        }

        @Override
        public int setYawnModSource(int source, float value) {
            return 0;
        }

        @Override
        public int setYawnModSlot(int slot, boolean enabled, int source,
                                  int targetType, int targetSlot,
                                  int targetParam, float amount) {
            slots.put(slot, new FakeSlot(enabled, source, targetType,
                    targetSlot, targetParam, amount));
            return 0;
        }
    }

    private static final class FakeSlot {
        final boolean enabled;
        final int source;
        final int targetType;
        final int targetSlot;
        final int targetParam;
        final float amount;

        FakeSlot(boolean enabled, int source, int targetType, int targetSlot,
                 int targetParam, float amount) {
            this.enabled = enabled;
            this.source = source;
            this.targetType = targetType;
            this.targetSlot = targetSlot;
            this.targetParam = targetParam;
            this.amount = amount;
        }
    }
}
