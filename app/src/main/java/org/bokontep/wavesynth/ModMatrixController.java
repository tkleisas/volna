package org.bokontep.wavesynth;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Editable sensor-to-parameter modulation matrix. */
public final class ModMatrixController {
    private static final String PREF_KEY = "modMatrixV1";
    private static final int MAX_ROWS = 6;
    private static final int MAX_SOURCES = SensorModulation.SOURCE_COUNT;

    private static final class Target {
        final String key;
        final String name;
        final int type;
        final int slot;
        final int param;
        final float min;
        final float max;

        Target(String key, String name, int type, int slot, int param,
               float min, float max) {
            this.key = key;
            this.name = name;
            this.type = type;
            this.slot = slot;
            this.param = param;
            this.min = min;
            this.max = max;
        }
    }

    private static final class Row {
        int slot;
        boolean enabled;
        int source = -1;
        String targetKey = "";
        Target target;
        float amount;
        LinearLayout view;
        ToggleButton enable;
        Spinner sourceSpinner;
        Spinner targetSpinner;
        SeekBar amountBar;
        TextView amountText;
        TextView title;
    }

    private final Activity activity;
    private final SynthEngine engine;
    private final AppPreferences prefs;
    private final LinearLayout container;
    private final Button addButton;
    private final Button calibrateButton;
    private final TextView statusText;
    private final SensorIndicatorView sensorIndicator;
    private final SensorIndicatorView modSensorIndicator;
    private final List<Row> rows = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private final SensorModulation sensors;
    private boolean resumed;
    private boolean updating;
    private int liveSource = -1;
    private float liveValue;
    private int sensorMask;

    public ModMatrixController(Activity activity, SynthEngine engine,
                               AppPreferences prefs) {
        this.activity = activity;
        this.engine = engine;
        this.prefs = prefs;
        container = activity.findViewById(R.id.modMatrixContainer);
        addButton = activity.findViewById(R.id.addModButton);
        calibrateButton = activity.findViewById(R.id.calibrateModButton);
        statusText = activity.findViewById(R.id.modStatusText);
        sensorIndicator = activity.findViewById(R.id.sensorIndicator);
        modSensorIndicator = activity.findViewById(R.id.modSensorIndicator);
        sensors = new SensorModulation(activity, (source, value) -> {
            if (engine != null) {
                engine.setYawnModSource(source, value);
            }
            liveSource = source;
            liveValue = value;
            updateSensorIndicator();
            updateStatus();
        });
    }

    public void attach() {
        if (container == null || addButton == null) {
            return;
        }
        addButton.setOnClickListener(v -> addRow(true));
        if (calibrateButton != null) {
            calibrateButton.setOnClickListener(v -> {
                sensors.calibrate();
                Toast.makeText(activity,
                        "Sensors armed for calibration; hold still.",
                        Toast.LENGTH_SHORT).show();
            });
        }
        refreshTargets(false);
        boolean loaded = loadRows();
        if (!loaded) {
            addRow(false);
        } else {
            rebuildRows();
            applyAll();
        }
        refreshSensors();
        updateStatus();
    }

    public void onEngineChanged() {
        refreshTargets(true);
        applyAll();
        refreshSensors();
        updateStatus();
    }

    public void refreshTargets() {
        refreshTargets(true);
    }

    private void refreshTargets(boolean apply) {
        targets.clear();
        targets.add(new Target("", "None", -1, 0, 0, 0.0f, 1.0f));
        final int mode = engine.getEngineMode();
        if (mode == SynthEngine.ENGINE_YAWN_WAVETABLE) {
            addInstrumentTargets(mode, new int[]{2, 3, 1, 14, 16, 15});
        } else if (mode == SynthEngine.ENGINE_YAWN_SUBTRACTIVE) {
            addInstrumentTargets(mode, new int[]{8, 9, 4, 6, 7, 21});
        }
        if (mode != SynthEngine.ENGINE_LEGACY) {
            int count = engine.getYawnAudioChainSize();
            for (int slot = 0; slot < count; slot++) {
                String effect = engine.getYawnAudioChainName(slot);
                int params = engine.getYawnAudioChainParamCount(slot);
                for (int param = 0; param < params; param++) {
                    if (engine.isYawnAudioChainParamBoolean(slot, param)
                            || engine.getYawnAudioChainParamLabelCount(
                                    slot, param) > 0) {
                        continue;
                    }
                    String key = "a:" + slot + ":" + param;
                    String name = "FX" + (slot + 1) + " " + effect + " · "
                            + engine.getYawnAudioChainParamName(slot, param);
                    targets.add(new Target(key, name, 2, slot, param,
                            engine.getYawnAudioChainParamMin(slot, param),
                            engine.getYawnAudioChainParamMax(slot, param)));
                }
            }
        }
        for (Row row : rows) {
            row.target = findTarget(row.targetKey);
        }
        if (apply) {
            rebuildRows();
            applyAll();
            refreshSensors();
            updateStatus();
        }
    }

    private void addInstrumentTargets(int mode, int[] indices) {
        int count = engine.getYawnInstrumentParamCount();
        for (int index : indices) {
            if (index < 0 || index >= count) {
                continue;
            }
            String name = engine.getYawnInstrumentParamName(index);
            if (name == null || name.isEmpty()) {
                continue;
            }
            targets.add(new Target("i:" + mode + ":" + index,
                    "Synth · " + name, 0, 0, index,
                    engine.getYawnInstrumentParamMin(index),
                    engine.getYawnInstrumentParamMax(index)));
        }
    }

    private Target findTarget(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (Target target : targets) {
            if (target.key.equals(key)) {
                return target;
            }
        }
        return null;
    }

    private void addRow(boolean persist) {
        if (rows.size() >= MAX_ROWS) {
            if (persist) {
                Toast.makeText(activity, "Modulation matrix is full",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        Row row = new Row();
        row.enabled = false;
        row.source = SensorModulation.ACCEL_X;
        row.amount = 0.0f;
        if (targets.size() > 1) {
            Target target = targets.get(1);
            row.target = target;
            row.targetKey = target.key;
        }
        row.slot = rows.size();
        rows.add(row);
        rebuildRows();
        applyAll();
        if (persist) {
            saveRows();
        }
        refreshSensors();
        updateStatus();
    }

    private void rebuildRows() {
        if (container == null) {
            return;
        }
        updating = true;
        container.removeAllViews();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.slot = i;
            row.view = buildRowView(row);
            container.addView(row.view);
        }
        updating = false;
        addButton.setEnabled(rows.size() < MAX_ROWS);
        addButton.setAlpha(rows.size() < MAX_ROWS ? 1.0f : 0.4f);
    }

    private LinearLayout buildRowView(Row row) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.bg_effect_slot);
        block.setClipToOutline(true);
        block.setPadding(dp(10), dp(8), dp(10), dp(10));
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blockParams.bottomMargin = dp(6);
        block.setLayoutParams(blockParams);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ToggleButton enable = new ToggleButton(activity);
        enable.setTextOn("ON");
        enable.setTextOff("OFF");
        enable.setTextSize(10);
        enable.setBackgroundResource(R.drawable.bg_toggle);
        enable.setTextColor(activity.getColorStateList(R.color.toggle_text));
        enable.setMinWidth(dp(54));
        enable.setMinimumWidth(dp(54));
        enable.setMinHeight(dp(34));
        enable.setMinimumHeight(dp(34));
        enable.setPadding(dp(8), 0, dp(8), 0);
        enable.setChecked(row.enabled);
        titleRow.addView(enable);
        row.enable = enable;

        TextView title = new TextView(activity);
        title.setText((row.slot + 1) + ". Modulation");
        title.setTextSize(13);
        title.setTextColor(activity.getColor(R.color.text_primary));
        title.setTypeface(Typeface.create("sans-serif-medium",
                Typeface.NORMAL));
        title.setPadding(dp(10), 0, 0, 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        titleRow.addView(title);
        row.title = title;

        Button remove = smallButton("Remove");
        remove.setOnClickListener(v -> {
            rows.remove(row);
            rebuildRows();
            applyAll();
            saveRows();
            refreshSensors();
            updateStatus();
        });
        titleRow.addView(remove);
        block.addView(titleRow);

        TextView sourceLabel = label("Source");
        block.addView(sourceLabel);
        Spinner sourceSpinner = new Spinner(activity);
        String[] sourceNames = new String[MAX_SOURCES + 1];
        sourceNames[0] = "None";
        for (int i = 0; i < MAX_SOURCES; i++) {
            sourceNames[i + 1] = SensorModulation.sourceName(i);
        }
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, sourceNames);
        sourceAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(sourceAdapter);
        sourceSpinner.setSelection(row.source + 1);
        sourceSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        block.addView(sourceSpinner);
        row.sourceSpinner = sourceSpinner;

        TextView targetLabel = label("Target");
        block.addView(targetLabel);
        Spinner targetSpinner = new Spinner(activity);
        String[] targetNames = new String[targets.size()];
        int targetSelection = 0;
        for (int i = 0; i < targets.size(); i++) {
            targetNames[i] = targets.get(i).name;
            if (targets.get(i).key.equals(row.targetKey)) {
                targetSelection = i;
            }
        }
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, targetNames);
        targetAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        targetSpinner.setAdapter(targetAdapter);
        targetSpinner.setSelection(targetSelection);
        targetSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        block.addView(targetSpinner);
        row.targetSpinner = targetSpinner;

        LinearLayout amountRow = new LinearLayout(activity);
        amountRow.setOrientation(LinearLayout.HORIZONTAL);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView amountLabel = label("Amount");
        amountLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        amountRow.addView(amountLabel);
        TextView amountText = new TextView(activity);
        amountText.setTextSize(11);
        amountText.setTypeface(Typeface.MONOSPACE);
        amountText.setTextColor(activity.getColor(R.color.accent));
        amountText.setText(amountString(row.amount));
        amountRow.addView(amountText);
        block.addView(amountRow);
        row.amountText = amountText;

        SeekBar amountBar = new SeekBar(activity);
        amountBar.setMax(200);
        amountBar.setProgress(Math.round((row.amount + 1.0f) * 100.0f));
        amountBar.setContentDescription("Modulation amount");
        block.addView(amountBar);
        row.amountBar = amountBar;

        enable.setOnCheckedChangeListener((button, checked) -> {
            row.enabled = checked;
            if (updating) {
                return;
            }
            applyNative(row);
            saveRows();
            refreshSensors();
            updateStatus();
        });
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (updating) {
                    return;
                }
                row.source = position <= 0 ? -1 : position - 1;
                applyNative(row);
                saveRows();
                refreshSensors();
                updateStatus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        targetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (updating || position < 0 || position >= targets.size()) {
                    return;
                }
                if (position == 0 && row.target == null
                        && !row.targetKey.isEmpty()) {
                    return;
                }
                Target target = targets.get(position);
                row.target = target;
                row.targetKey = target.key;
                applyNative(row);
                saveRows();
                refreshSensors();
                updateStatus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        amountBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                row.amount = (progress - 100) / 100.0f;
                amountText.setText(amountString(row.amount));
                applyNative(row);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveRows();
            }
        });
        return block;
    }

    private TextView label(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(11);
        view.setTextColor(activity.getColor(R.color.text_secondary));
        view.setPadding(0, dp(8), 0, dp(3));
        return view;
    }

    private Button smallButton(String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(9);
        button.setTextColor(activity.getColorStateList(
                R.color.button_text_selector));
        button.setBackgroundResource(R.drawable.bg_button_secondary);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(6), dp(4), dp(6), dp(4));
        return button;
    }

    private static String amountString(float amount) {
        int percent = Math.round(amount * 100.0f);
        return String.format(Locale.US, "%+d%%", percent);
    }

    private void applyNative(Row row) {
        if (engine == null) {
            return;
        }
        Target target = row.target;
        boolean active = row.enabled && target != null && row.source >= 0;
        engine.setYawnModSlot(row.slot, active, row.source,
                target != null ? target.type : -1,
                target != null ? target.slot : 0,
                target != null ? target.param : 0,
                row.amount);
    }

    private void applyAll() {
        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < rows.size()) {
                applyNative(rows.get(i));
            } else if (engine != null) {
                engine.setYawnModSlot(i, false, 0, -1, 0, 0, 0.0f);
            }
        }
    }

    private void refreshSensors() {
        boolean[] required = new boolean[SensorModulation.SOURCE_COUNT];
        boolean any = false;
        if (engine != null
                && engine.getEngineMode() != SynthEngine.ENGINE_LEGACY) {
            for (Row row : rows) {
                if (row.enabled && row.target != null && row.source >= 0) {
                    required[row.source] = true;
                    any = true;
                }
            }
        }
        sensors.setRequiredSources(required);
        if (resumed && any) {
            sensors.start();
        } else {
            sensors.stop();
        }
        sensorMask = 0;
        if (required[SensorModulation.ACCEL_X]
                || required[SensorModulation.ACCEL_Y]
                || required[SensorModulation.ACCEL_Z]) {
            sensorMask |= 1;
        }
        if (required[SensorModulation.GYRO_X]
                || required[SensorModulation.GYRO_Y]
                || required[SensorModulation.GYRO_Z]) {
            sensorMask |= 2;
        }
        if (required[SensorModulation.MAG_X]
                || required[SensorModulation.MAG_Y]
                || required[SensorModulation.MAG_Z]
                || required[SensorModulation.MAG_MAGNITUDE]) {
            sensorMask |= 4;
        }
        updateSensorIndicator();
        if (calibrateButton != null) {
            calibrateButton.setEnabled(any && sensors.needsSensors());
            calibrateButton.setAlpha(
                    any && sensors.needsSensors() ? 1.0f : 0.4f);
        }
    }

    private void updateSensorIndicator() {
        boolean active = sensors.isRunning();
        float shownValue = active ? liveValue : 0.0f;
        if (sensorIndicator != null) {
            sensorIndicator.setState(active, sensorMask, shownValue);
        }
        if (modSensorIndicator != null) {
            modSensorIndicator.setState(active, sensorMask, shownValue);
        }
    }

    private void updateStatus() {
        if (statusText == null) {
            return;
        }
        if (engine != null
                && engine.getEngineMode() == SynthEngine.ENGINE_LEGACY) {
            statusText.setText("Modulation is available in YAWN engines");
        } else {
            String status = sensors.statusText();
            if (sensors.isRunning() && liveSource >= 0) {
                status += String.format(Locale.US, "  ·  %s %+.2f",
                        SensorModulation.sourceName(liveSource), liveValue);
            }
            statusText.setText(status);
        }
    }

    public void onResume() {
        resumed = true;
        refreshSensors();
        updateStatus();
    }

    public void onPause() {
        resumed = false;
        sensors.stop();
        updateSensorIndicator();
        updateStatus();
    }

    private void saveRows() {
        JSONArray array = new JSONArray();
        for (Row row : rows) {
            JSONObject object = new JSONObject();
            try {
                object.put("enabled", row.enabled);
                object.put("source", row.source);
                object.put("target", row.targetKey);
                object.put("amount", row.amount);
            } catch (JSONException ignored) {
            }
            array.put(object);
        }
        prefs.writeString(PREF_KEY, array.toString());
    }

    private boolean loadRows() {
        String json = prefs.readString(PREF_KEY, "");
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length() && i < MAX_ROWS; i++) {
                JSONObject object = array.getJSONObject(i);
                Row row = new Row();
                row.enabled = object.optBoolean("enabled", false);
                row.source = object.optInt("source", -1);
                row.targetKey = object.optString("target", "");
                row.amount = (float) object.optDouble("amount", 0.0);
                row.target = findTarget(row.targetKey);
                row.slot = rows.size();
                rows.add(row);
            }
            return !rows.isEmpty();
        } catch (JSONException e) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value
                * activity.getResources().getDisplayMetrics().density);
    }
}
