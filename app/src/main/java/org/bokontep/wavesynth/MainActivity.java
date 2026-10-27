package org.bokontep.wavesynth;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.VolumeShaper;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.bokontep.midi.MidiConstants;
import org.bokontep.midi.MidiInputPortSelector;
import org.bokontep.midi.MidiOutputPortConnectionSelector;
import org.bokontep.midi.MidiPortConnector;
import org.bokontep.midi.MidiTools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    static final String TAG = "Volna";

    private long lastTouchEventTime = 0;


    private VolnaMidiReceiver midiReceiver;
    private Paint paint;
    private byte[] mByteBuffer = new byte[3];
    private int vel = -1;
    public long enterSettings;
    private int touchPoints = 0;
    private boolean play = false;
    private boolean record = false;
    private int maxSpread = 0;
    private int osc1Wave = 0;
    private int osc1WaveControl = 0;
    private int osc2Wave = 0;
    private int osc2WaveControl = 0;
    private int delayLevel = 0;
    private int delayTime = 0;
    private int delayFeedback = 0;
    private int tet = 12;

    private float tune = 440.0f;
    private float octaveFactor = 2.0f;
    private float pitchBendAmount = 0.0f;
    private long settingsPressTime = 5000;
    private String rootNoteStr = "";
    private boolean red = false;
    private MidiManager midiManager;
    public MidiOutputPortConnectionSelector midiPortSelector;
    public MidiInputPortSelector midiInputPortSelector;
    private int updateInterval = 20;
    private Handler mHandler;
    private Runnable screenUpdater;
    private Scope scope;

    private WavetableStore wavetableStore;
    private TextView customWavetableText;
    private Spinner engineSpinner;
    private TextView engineChip;
    private LinearLayout soundControls;
    private LinearLayout fxControls;
    private CollapsibleSection midiFxSection;
    private CollapsibleSection audioFxSection;
    private float yawnTempo = 120.0f;
    private float yawnMasterVolume = 0.8f;

    private View performanceArea;
    private View settingsPanel;
    private TextView performanceInfoText;
    private TextView scopeModeWave;
    private TextView scopeModeFft;
    private TextView scopeModeBoth;
    private int scopeMode = Scope.MODE_BOTH;
    private SpectrumAnalyzer spectrumAnalyzer;
    private float[] spectrumBars;
    private int spectrumFrame;
    private final List<View> settingsCards = new ArrayList<>();
    private final List<String> settingsCardTabs = new ArrayList<>();
    private final List<TextView> settingsTabButtons = new ArrayList<>();
    private final List<String> settingsTabKeys = new ArrayList<>();
    private String currentSettingsTab = "play";

    private ModMatrixController modMatrix;
    private RtpMidiClient rtpMidiClient;
    private EditText rtpMidiHostEditText;
    private EditText rtpMidiPortEditText;
    private ToggleButton rtpMidiToggleButton;
    private TextView rtpMidiStatusText;
    private final Runnable rtpMidiRestart = () -> {
        if (rtpMidiToggleButton != null && rtpMidiToggleButton.isChecked()) {
            startRtpMidi();
        }
    };

    private final ActivityResultLauncher<String[]> importWavetableLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onWavetableFileSelected);

    private ToggleButton redToggleButton;
    private ToggleButton legatoToggleButton;
    private ToggleButton safetyLimiterToggle;
    private ToggleButton polyphonyLevelingToggle;
    private Spinner scaleSpinner;
    private Button editScaleButton;
    private ScaleStore scaleStore;
    private ScaleDefinition currentScaleDefinition;
    private String currentScaleId = "";
    private final List<ScaleDefinition> scaleOptions = new ArrayList<>();
    private RegisterLayout registerLayout;
    private SeekBar registerCountSeekBar;
    private TextView registerCountText;
    private Spinner registerUnitSpinner;
    private Spinner registerPresetSpinner;
    private LinearLayout registerControls;
    private boolean updatingScaleSpinner;
    private boolean updatingRegisterUi;
    private boolean legato = true;
    private boolean safetyLimiter = true;
    private boolean polyphonyLeveling = true;
    private Spinner rootNoteSpinner;
    private SeekBar tetSeekBar;
    private SeekBar tuneSeekBar;
    private SeekBar octaveFactorSeekBar;
    private SeekBar pitchBendSeekBar;
    private SeekBar osc1AttackSeekBar;
    private SeekBar osc1DecaySeekBar;
    private SeekBar osc1SustainSeekBar;
    private SeekBar osc1ReleaseSeekBar;
    private SeekBar osc2AttackSeekBar;
    private SeekBar osc2DecaySeekBar;
    private SeekBar osc2SustainSeekBar;
    private SeekBar osc2ReleaseSeekBar;
    private SeekBar maxSpreadSeekBar;
    private SeekBar osc1WaveSeekBar;
    private SeekBar osc1WaveControlSeekBar;
    private SeekBar osc2WaveSeekBar;
    private SeekBar osc2WaveControlSeekBar;
    private SeekBar delayLevelSeekBar;
    private SeekBar delayTimeSeekBar;
    private SeekBar delayFeedbackSeekBar;
    private SeekBar gridSizeSeekBar;
    private TextView gridSizeTextView;
    private TextView dspLoadText;
    private int dspUiCounter;
    private TextView tuneTextView;
    private TextView tetTextView;
    private TextView octaveFactorTextView;
    private TextView pitchBendTextView;
    private TextView osc1AttackTextView;
    private TextView osc1DecayTextView;
    private TextView osc1SustainTextView;
    private TextView osc1ReleaseTextView;
    private TextView osc2AttackTextView;
    private TextView osc2DecayTextView;
    private TextView osc2SustainTextView;
    private TextView osc2ReleaseTextView;
    private TextView oscSpreadTextView;
    private WaveDisplay osc1WaveDisplay;
    private WaveDisplay osc2WaveDisplay;

    private TextView osc1WaveTextView;
    private TextView osc2WaveTextView;
    private TextView osc1WaveControlTextView;
    private TextView osc2WaveControlTextView;
    private TextView tvVersion;
    private Button settingsButton;
    private Button recButton;
    private Button playButton;
    private LoopMeterView loopMeter;
    private View loopClearButton;
    private long recordStartTime;
    private long playStartTime;
    private ScrollView optionsScrollView;
    private View menuView;
    private int rootNote = 36;
    private int xNoteScale = 160;
    private MicrotonalPitchMapper pitchMapper;
    private MpeChannelAllocator mpeChannelAllocator;
    private final SparseArray<TouchVoice> touchVoices = new SparseArray<>();
    private static final String PREF_YAWN_STATE = "yawnStateV1";

    private AppPreferences prefs;
    private SynthEngine engine;
    private final Runnable saveSynthStateRunnable = this::saveSynthStateNow;
    private boolean resumed;
    private final Runnable audioResumeRetry = () -> {
        if (resumed && engine != null) {
            engine.resumeAudio();
        }
    };
    private Boolean pressureSupported;
    private Boolean sizeSupported;
    private String[] rootNotes =
            {
                    "C0(0)", "C#0(1)", "D0(2)", "D#0(3)", "E0(4)", "F0(5)", "F#0(6)", "G0(7)", "G#0(8)", "A0(9)", "A#0(10)", "B0(11)",
                    "C1(12)", "C#1(13)", "D1(14)", "D#1(15)", "E1(16)", "F1(17)", "F#1(18)", "G1(19)", "G#1(20)", "A1(21)", "A#1(22)", "B1(23)",
                    "C2(24)", "C#2(25)", "D2(26)", "D#2(27)", "E2(28)", "F2(29)", "F#2(30)", "G2(31)", "G#2(32)", "A2(33)", "A#2(34)", "B2(35)",
                    "C3(36)", "C#3(37)", "D3(38)", "D#3(39)", "E3(40)", "F3(41)", "F#3(42)", "G3(43)", "G#3(44)", "A3(45)", "A#3(46)", "B3(47)",
                    "C4(48)", "C#4(49)", "D4(50)", "D#4(51)", "E4(52)", "F4(53)", "F#4(54)", "G4(55)", "G#4(56)", "A4(57)", "A#4(58)", "B4(59)",
                    "C5(60)", "C#5(61)", "D5(62)", "D#5(63)", "E5(64)", "F5(65)", "F#5(66)", "G5(67)", "G#5(68)", "A5(69)", "A#5(70)", "B5(71)",
                    "C6(72)", "C#6(73)", "D6(74)", "D#6(75)", "E6(76)", "F6(77)", "F#6(78)", "G6(79)", "G#6(80)", "A6(81)", "A#6(82)", "B6(83)"

            };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        super.onCreate(savedInstanceState);

        prefs = new AppPreferences(this);
        engine = new SynthEngine(this, 44100);
        wavetableStore = new WavetableStore(this);
        scaleStore = new ScaleStore(prefs);
        delayLevel = prefs.readInt("delayLevel",0);
        delayTime = prefs.readInt("delayTime",0);
        delayFeedback = prefs.readInt("delayFeedback",0);
        engine.setEngineMode(prefs.readInt("engineMode",
                SynthEngine.ENGINE_YAWN_WAVETABLE));
        yawnTempo = prefs.readInt("yawnTempo", 1200) / 10.0f;
        yawnMasterVolume = prefs.readInt("yawnMasterVolume", 800) / 1000.0f;
        engine.initAudio();
        engine.resetDspPeak();
        if (engine.getEngineMode() != SynthEngine.ENGINE_LEGACY) {
            engine.setYawnTempo(yawnTempo);
            engine.setYawnMasterVolume(yawnMasterVolume);
        }

        tune = (prefs.readInt("tune", 4400) / 10.0f);
        tet = prefs.readInt("tet", 12);
        octaveFactor = (prefs.readInt("octaveFactor",2000)/1000.0f);
        pitchBendAmount = Math.max(0.0f, Math.min(1.0f,
                prefs.readInt("pitchBend", 0) / 100.0f));
        red = prefs.readInt("red", 0) == 0 ? false : true;
        legato = prefs.readInt("legato", 0)==0?false:true;
        safetyLimiter = prefs.readInt("safetyLimiter", 1) != 0;
        polyphonyLeveling = prefs.readInt("polyphonyLeveling", 1) != 0;

        rootNote = prefs.readInt("rootNote", 35);
        xNoteScale = prefs.readInt("xNoteScale", 160);
        currentScaleId = prefs.readString("currentScaleId", "");
        if (currentScaleId.isEmpty()) {
            int legacyScale = prefs.readInt("currentScale", 0);
            if (tet == 12 && legacyScale >= 0 && legacyScale < 14) {
                currentScaleId = scaleStore.scalesForTet(12)
                        .get(legacyScale).id;
            }
        }
        currentScaleDefinition = scaleStore.find(currentScaleId, tet);
        currentScaleId = currentScaleDefinition.id;
        prefs.writeString("currentScaleId", currentScaleId);
        registerLayout = RegisterLayout.fromPreferences(
                prefs, currentScaleDefinition.size());
        maxSpread = prefs.readInt("maxSpread", 0);
        osc1Wave = prefs.readInt("osc1Wave", 0);
        osc2Wave = prefs.readInt("osc2Wave", 0);
        osc1WaveControl = prefs.readInt("osc1WaveControl", 255);
        osc2WaveControl = prefs.readInt("osc2WaveControl", 255);
        engine.setOsc1Volume(prefs.readInt("osc1Volume", 127));
        engine.setOsc2Volume(prefs.readInt("osc2Volume", 127));
        engine.setOsc1Attack(prefs.readInt("osc1Attack", 10));
        engine.setOsc1Decay(prefs.readInt("osc1Decay", 0));
        engine.setOsc1Sustain(prefs.readInt("osc1Sustain", 127));
        engine.setOsc1Release(prefs.readInt("osc1Release", 0));
        engine.setOsc2Attack(prefs.readInt("osc2Attack", 10));
        engine.setOsc2Decay(prefs.readInt("osc2Decay", 0));
        engine.setOsc2Sustain(prefs.readInt("osc2Sustain", 127));
        engine.setOsc2Release(prefs.readInt("osc2Release", 0));
        engine.setDelayLevel(delayLevel);
        engine.setDelayTime(delayTime);
        engine.setDelayFeedback(delayFeedback);
        engine.setTune(tune);
        engine.setTet(tet);
        engine.setOctaveFactor(octaveFactor);
        engine.initSynthParameters();
        engine.sendMidiCC(0, 16, osc1Wave);
        engine.sendMidiCC(0, 17, osc2Wave);
        rootNoteStr = this.midiNoteToString(rootNote);
        mpeChannelAllocator = new MpeChannelAllocator();
        updatePitchMapper();
        restoreSynthState();

        setContentView(R.layout.activity_main);
        paint = new Paint();
        paint.setColor(0xffff0000);
        mHandler = new Handler();
        tvVersion = findViewById(R.id.tv_version);
        tvVersion.setText("волна v."+BuildConfig.VERSION_NAME);
        engineSpinner = (Spinner) findViewById(R.id.engineSpinner);
        final String[] engineNames = {
                "Volna Classic",
                "YAWN Wavetable",
                "YAWN Subtractive",
                "YAWN Karplus-Strong"
        };
        engineSpinner.setAdapter(spinnerAdapter(engineNames));
        engineChip = findViewById(R.id.engineChip);
        engineChip.setText(engineNames[engine.getEngineMode()].toUpperCase(Locale.US));
        engineChip.setOnClickListener(v -> {
            showSettings();
            selectSettingsTab("sound");
        });
        engineSpinner.setSelection(engine.getEngineMode());
        engineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (position == engine.getEngineMode()) {
                    return;
                }
                saveSynthStateNow();
                engine.switchEngine(position);
                engine.resetDspPeak();
                applySafetySettings();
                prefs.writeInt("engineMode", position);
                if (position != SynthEngine.ENGINE_LEGACY) {
                    restoreSynthState();
                }
                if (engineChip != null) {
                    engineChip.setText(engineNames[position].toUpperCase(Locale.US));
                }
                rebuildYawnControls();
                updateWavetableViews();
                if (wavetableStore.hasStoredWavetable()) {
                    restoreSavedWavetable();
                }
                record = false;
                play = false;
                recButton.setSelected(false);
                playButton.setSelected(false);
                retriggerActiveTouches();
                if (modMatrix != null) {
                    modMatrix.onEngineChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        soundControls = findViewById(R.id.soundControls);
        fxControls = findViewById(R.id.fxControls);
        rebuildYawnControls();
        installCollapsibleXmlSections();
        setupSettingsTabs();
        redToggleButton = (ToggleButton) findViewById(R.id.redToggleButton);
        redToggleButton.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            scope.setRed(true);
                            osc1WaveDisplay.setRed(true);
                            osc2WaveDisplay.setRed(true);
                            prefs.writeInt("red", 1);
                        } else {
                            scope.setRed(false);
                            osc1WaveDisplay.setRed(false);
                            osc2WaveDisplay.setRed(false);
                            prefs.writeInt("red", 0);
                        }
                    }
                }
        );
        legatoToggleButton = (ToggleButton)findViewById(R.id.legatoToggleButton);
        legatoToggleButton.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        legato=isChecked;
                        prefs.writeInt("legato", legato?1:0);
                    }
                }
        );
        safetyLimiterToggle = findViewById(R.id.safetyLimiterToggle);
        safetyLimiterToggle.setChecked(safetyLimiter);
        safetyLimiterToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            safetyLimiter = isChecked;
            prefs.writeInt("safetyLimiter", isChecked ? 1 : 0);
            applySafetySettings();
        });
        polyphonyLevelingToggle = findViewById(R.id.polyphonyLevelingToggle);
        polyphonyLevelingToggle.setChecked(polyphonyLeveling);
        polyphonyLevelingToggle.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    polyphonyLeveling = isChecked;
                    prefs.writeInt("polyphonyLeveling", isChecked ? 1 : 0);
                    applySafetySettings();
                });
        applySafetySettings();

        performanceArea = findViewById(R.id.performanceArea);
        performanceInfoText = findViewById(R.id.performanceInfoText);
        scope = findViewById(R.id.mscope);
        scope.setXNoteScale(xNoteScale);
        scope.setRed(red);
        setupPerformanceTouch();
        setupScopeModeControls();
        scope.setNoteLabelProvider((cell, zone) -> labelForCell(cell, zone));
        scope.setRegisterCount(registerLayout.count());
        updateScopeText();
        rootNoteSpinner = (Spinner) findViewById(R.id.rootNoteSpinner);
        final ArrayAdapter<String> rootNoteAdapter = spinnerAdapter(rootNotes);

        rootNoteSpinner.setAdapter(rootNoteAdapter);
        rootNoteSpinner.setSelection(rootNote);

        rootNoteSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position >= 0 && position < rootNoteAdapter.getCount()) {
                            applySettings();
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                }
        );
        setupScaleSpinner();
        setupRegisterControls();
        scope.setRegisterCount(registerLayout.count());
        this.tetSeekBar = (SeekBar) findViewById(R.id.tetSeekBar);
        this.tetSeekBar.setProgress(tet);
        this.tetTextView = (TextView) findViewById(R.id.tetText);
        this.tetTextView.setText("TET: " + tet);
        this.tuneSeekBar = (SeekBar) findViewById(R.id.tuneSeekBar);
        this.tuneSeekBar.setProgress((int) (tune * 10));
        this.octaveFactorTextView = (TextView)findViewById(R.id.octaveFactorText);
        this.octaveFactorTextView.setText(String.format(Locale.US, "Octave factor: %.2f", octaveFactor));
        this.octaveFactorSeekBar = (SeekBar)findViewById(R.id.octaveFactorSeekBar);
        this.octaveFactorSeekBar.setProgress((int) (octaveFactor * 1000));
        this.pitchBendTextView = (TextView) findViewById(R.id.pitchBendText);
        this.pitchBendTextView.setText("Pitch bend: "
                + Math.round(pitchBendAmount * 100.0f) + "%");
        this.pitchBendSeekBar = (SeekBar) findViewById(R.id.pitchBendSeekBar);
        this.pitchBendSeekBar.setProgress(Math.round(pitchBendAmount * 100.0f));


        this.osc1AttackSeekBar = (SeekBar) findViewById(R.id.osc1AttackSeekBar);
        this.osc1AttackSeekBar.setProgress(engine.getOsc1Attack());
        this.osc1DecaySeekBar = (SeekBar) findViewById(R.id.osc1DecaySeekBar);
        this.osc1DecaySeekBar.setProgress(engine.getOsc1Decay());
        this.osc1SustainSeekBar = (SeekBar) findViewById(R.id.osc1SustainSeekBar);
        this.osc1SustainSeekBar.setProgress(engine.getOsc1Sustain());
        this.osc1ReleaseSeekBar = (SeekBar) findViewById(R.id.osc1ReleaseSeekBar);
        this.osc1ReleaseSeekBar.setProgress(engine.getOsc1Release());
        this.osc2AttackSeekBar = (SeekBar) findViewById(R.id.osc2AttackSeekBar);
        this.osc2AttackSeekBar.setProgress(engine.getOsc2Attack());
        this.osc2DecaySeekBar = (SeekBar) findViewById(R.id.osc2DecaySeekBar);
        this.osc2DecaySeekBar.setProgress(engine.getOsc2Decay());
        this.osc2SustainSeekBar = (SeekBar) findViewById(R.id.osc2SustainSeekBar);
        this.osc2SustainSeekBar.setProgress(engine.getOsc2Sustain());
        this.osc2ReleaseSeekBar = (SeekBar) findViewById(R.id.osc2ReleaseSeekBar);
        this.osc2ReleaseSeekBar.setProgress(engine.getOsc2Release());
        this.maxSpreadSeekBar = (SeekBar) findViewById(R.id.maxSpreadSeekBar);
        this.maxSpreadSeekBar.setProgress(this.maxSpread);
        this.gridSizeSeekBar = (SeekBar) findViewById(R.id.gridSizeSeekBar);
        this.gridSizeSeekBar.setProgress(this.xNoteScale);
        this.gridSizeTextView = (TextView) findViewById(R.id.gridSizeText);
        updateGridSizeLabel();
        this.dspLoadText = (TextView) findViewById(R.id.dspLoadText);
        findViewById(R.id.resetDspPeakButton).setOnClickListener(v -> {
            if (engine != null) {
                engine.resetDspPeak();
            }
            if (dspLoadText != null) {
                dspLoadText.setText(String.format(Locale.US,
                        "DSP %5.1f%%  pk %5.1f%%", 0.0f, 0.0f));
            }
        });
        this.osc1WaveSeekBar = (SeekBar) findViewById(R.id.osc1WaveSeekBar);
        this.osc1WaveSeekBar.setProgress(osc1Wave);
        this.osc2WaveSeekBar = (SeekBar) findViewById(R.id.osc2WaveSeekBar);
        this.osc2WaveSeekBar.setProgress(osc2Wave);
        this.osc1WaveControlSeekBar = (SeekBar) findViewById(R.id.osc1WaveControlSeekBar);
        this.osc1WaveControlSeekBar.setProgress(osc1WaveControl);
        this.osc2WaveControlSeekBar = (SeekBar) findViewById(R.id.osc2WaveControlSeekBar);
        this.osc2WaveControlSeekBar.setProgress(osc2WaveControl);
        this.delayLevelSeekBar = (SeekBar) findViewById(R.id.delayLevelSeekBar);
        this.delayLevelSeekBar.setProgress(delayLevel);
        this.delayTimeSeekBar = (SeekBar) findViewById(R.id.delayTimeSeekBar);
        this.delayTimeSeekBar.setProgress(delayTime);
        this.delayFeedbackSeekBar = (SeekBar) findViewById(R.id.delayFeedbackSeekBar);
        this.delayFeedbackSeekBar.setProgress(delayFeedback);
        this.tuneTextView = (TextView) findViewById(R.id.tuneText);
        this.tuneTextView.setText(String.format(Locale.US, "A4 frequency: %.1f Hz", tune));
        this.osc1AttackTextView = (TextView) findViewById(R.id.osc1AttackText);
        this.osc1AttackTextView.setText("Osc 1 attack: " + engine.getOsc1Attack());
        this.osc1DecayTextView = (TextView) findViewById(R.id.osc1DecayText);
        this.osc1DecayTextView.setText("Osc 1 decay: " + engine.getOsc1Decay());
        this.osc1SustainTextView = (TextView) findViewById(R.id.osc1SustainText);
        this.osc1SustainTextView.setText("Osc 1 sustain: " + engine.getOsc1Sustain());
        this.osc1ReleaseTextView = (TextView) findViewById(R.id.osc1ReleaseText);
        this.osc1ReleaseTextView.setText("Osc 1 release: " + engine.getOsc1Release());
        this.osc2AttackTextView = (TextView) findViewById(R.id.osc2AttackText);
        this.osc2AttackTextView.setText("Osc 2 attack: " + engine.getOsc2Attack());
        this.osc2DecayTextView = (TextView) findViewById(R.id.osc2DecayText);
        this.osc2DecayTextView.setText("Osc 2 decay: " + engine.getOsc2Decay());
        this.osc2SustainTextView = (TextView) findViewById(R.id.osc2SustainText);
        this.osc2SustainTextView.setText("Osc 2 sustain: " + engine.getOsc2Sustain());
        this.osc2ReleaseTextView = (TextView) findViewById(R.id.osc2ReleaseText);
        this.osc2ReleaseTextView.setText("Osc 2 release: " + engine.getOsc2Release());

        this.oscSpreadTextView = (TextView) findViewById(R.id.oscSpreadText);
        this.oscSpreadTextView.setText("Unison spread: " + maxSpread);
        this.osc1WaveTextView = (TextView) findViewById(R.id.osc1WaveText);
        this.osc1WaveTextView.setText("Osc 1 wavetable: " + osc1Wave);
        this.osc2WaveTextView = (TextView) findViewById(R.id.osc2WaveText);
        this.osc2WaveTextView.setText("Osc 2 wavetable: " + osc2Wave);
        this.osc1WaveControlTextView = (TextView) findViewById(R.id.osc1WaveControlText);
        this.osc1WaveControlTextView.setText("Osc 1 wave control: " + osc1WaveControl);
        this.osc2WaveControlTextView = (TextView) findViewById(R.id.osc2WaveControlText);
        this.osc2WaveControlTextView.setText("Osc 2 wave control: " + osc2WaveControl);
        this.osc1WaveDisplay = (WaveDisplay) findViewById(R.id.osc1WaveDisplay);
        this.osc1WaveDisplay.setData(engine.getWavetable(osc1Wave));
        this.osc1WaveDisplay.setRed(red);
        //this.osc1WaveDisplay.invalidate();
        this.osc2WaveDisplay = (WaveDisplay) findViewById(R.id.osc2WaveDisplay);
        this.osc2WaveDisplay.setData(engine.getWavetable(osc2Wave));
        this.osc2WaveDisplay.setRed(red);
        this.customWavetableText = (TextView) findViewById(R.id.customWavetableText);
        findViewById(R.id.importWavetableButton).setOnClickListener(
                v -> importWavetableLauncher.launch(new String[]{"*/*"}));
        findViewById(R.id.resetWavetableButton).setOnClickListener(
                v -> restoreFactoryWavetable("Factory wavetables restored"));
        restoreSavedWavetable();
        //this.osc2WaveDisplay.invalidate();
        SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser)
                    return;
                int id = seekBar.getId();
                if (id == R.id.tuneSeekBar) {
                    tune = ((float) progress) / 10.0f;
                    tuneTextView.setText(String.format(Locale.US, "A4 frequency: %.1f Hz", tune));
                    engine.setTune(tune);
                    prefs.writeInt("tune", (int) (tune * 10));
                    updatePitchMapper();
                } else if (id == R.id.tetSeekBar) {
                    tet = progress;
                    engine.setTet(tet);
                    tetTextView.setText("TET: " + tet);
                    prefs.writeInt("tet", tet);
                    rebuildScaleSpinner();
                    registerLayout.ensureDegreeDefaults(
                            currentScaleDefinition.size());
                    updatePitchMapper();
                    refreshRegisterUi();
                    updateScopeText();
                } else if (id == R.id.octaveFactorSeekBar) {
                    octaveFactor = ((float)progress) / 1000.0f;
                    octaveFactorTextView.setText(String.format(Locale.US, "Octave factor: %.2f", octaveFactor));
                    engine.setOctaveFactor(octaveFactor);
                    prefs.writeInt("octaveFactor",(int)(octaveFactor*1000));
                    updatePitchMapper();
                } else if (id == R.id.pitchBendSeekBar) {
                    pitchBendAmount = progress / 100.0f;
                    pitchBendTextView.setText("Pitch bend: " + progress + "%");
                    prefs.writeInt("pitchBend", progress);
                    refreshActiveTouchPitch();
                } else if (id == R.id.osc1AttackSeekBar) {
                    engine.setOsc1Attack(progress);
                    osc1AttackTextView.setText("Osc 1 attack: " + engine.getOsc1Attack());
                    prefs.writeInt("osc1Attack", engine.getOsc1Attack());
                    engine.sendMidiCC(0, 18, engine.getOsc1Attack());
                } else if (id == R.id.osc1DecaySeekBar) {
                    engine.setOsc1Decay(progress);
                    osc1DecayTextView.setText("Osc 1 decay: " + engine.getOsc1Decay());
                    prefs.writeInt("osc1Decay", engine.getOsc1Decay());
                    engine.sendMidiCC(0, 19, engine.getOsc1Decay());
                } else if (id == R.id.osc1SustainSeekBar) {
                    engine.setOsc1Sustain(progress);
                    osc1SustainTextView.setText("Osc 1 sustain: " + engine.getOsc1Sustain());
                    prefs.writeInt("osc1Sustain", engine.getOsc1Sustain());
                    engine.sendMidiCC(0, 20, engine.getOsc1Sustain());
                } else if (id == R.id.osc1ReleaseSeekBar) {
                    engine.setOsc1Release(progress);
                    osc1ReleaseTextView.setText("Osc 1 release: " + engine.getOsc1Release());
                    prefs.writeInt("osc1Release", engine.getOsc1Release());
                    engine.sendMidiCC(0, 21, engine.getOsc1Release());
                } else if (id == R.id.osc2AttackSeekBar) {
                    engine.setOsc2Attack(progress);
                    osc2AttackTextView.setText("Osc 2 attack: " + engine.getOsc2Attack());
                    prefs.writeInt("osc2Attack", engine.getOsc2Attack());
                    engine.sendMidiCC(0, 22, engine.getOsc2Attack());
                } else if (id == R.id.osc2DecaySeekBar) {
                    engine.setOsc2Decay(progress);
                    osc2DecayTextView.setText("Osc 2 decay: " + engine.getOsc2Decay());
                    prefs.writeInt("osc2Decay", engine.getOsc2Decay());
                    engine.sendMidiCC(0, 23, engine.getOsc2Decay());
                } else if (id == R.id.osc2SustainSeekBar) {
                    engine.setOsc2Sustain(progress);
                    osc2SustainTextView.setText("Osc 2 sustain: " + engine.getOsc2Sustain());
                    prefs.writeInt("osc2Sustain", engine.getOsc2Sustain());
                    engine.sendMidiCC(0, 24, engine.getOsc2Sustain());
                } else if (id == R.id.osc2ReleaseSeekBar) {
                    engine.setOsc2Release(progress);
                    osc2ReleaseTextView.setText("Osc 2 release: " + engine.getOsc2Release());
                    prefs.writeInt("osc2Release", engine.getOsc2Release());
                    engine.sendMidiCC(0, 25, engine.getOsc2Release());
                } else if (id == R.id.maxSpreadSeekBar) {
                    maxSpread = progress;
                    oscSpreadTextView.setText("Unison spread: " + maxSpread);
                    prefs.writeInt("maxSpread", maxSpread);
                } else if (id == R.id.osc1WaveSeekBar) {
                    osc1Wave = progress;
                    osc1WaveDisplay.setData(engine.getWavetable(osc1Wave));
                    engine.sendMidiCC(0,16,osc1Wave);
                    osc1WaveTextView.setText("Osc 1 wavetable: " + osc1Wave);
                    osc1WaveDisplay.invalidate();
                    prefs.writeInt("osc1Wave", osc1Wave);
                } else if (id == R.id.osc2WaveSeekBar) {
                    osc2Wave = progress;
                    osc2WaveDisplay.setData(engine.getWavetable(osc2Wave));
                    engine.sendMidiCC(0,17,osc2Wave);
                    osc2WaveTextView.setText("Osc 2 wavetable: " + osc2Wave);
                    osc2WaveDisplay.invalidate();
                    prefs.writeInt("osc2Wave", osc2Wave);
                } else if (id == R.id.osc1WaveControlSeekBar) {
                    osc1WaveControl = progress;
                    osc1WaveControlTextView.setText("Osc 1 wave control: " + osc1WaveControl);
                    prefs.writeInt("osc1WaveControl", osc1WaveControl);
                } else if (id == R.id.osc2WaveControlSeekBar) {
                    osc2WaveControl = progress;
                    osc2WaveControlTextView.setText("Osc 2 wave control: " + osc2WaveControl);
                    prefs.writeInt("osc2WaveControl", osc2WaveControl);
                } else if (id == R.id.delayLevelSeekBar) {
                    delayLevel = progress;
                    engine.setDelayLevel(delayLevel);
                    prefs.writeInt("delayLevel",delayLevel);
                } else if (id == R.id.delayTimeSeekBar) {
                    delayTime = progress;
                    engine.setDelayTime(delayTime);
                    prefs.writeInt("delayTime",delayTime);
                } else if (id == R.id.delayFeedbackSeekBar) {
                    delayFeedback = progress;
                    engine.setDelayFeedback(delayFeedback);
                    prefs.writeInt("delayFeedback",delayFeedback);
                } else if (id == R.id.gridSizeSeekBar) {
                    xNoteScale = progress;
                    prefs.writeInt("xNoteScale", xNoteScale);
                    scope.setXNoteScale(xNoteScale);
                    scope.invalidate();
                    updateGridSizeLabel();
                    updatePitchMapper();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        };
        this.tuneSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.tetSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.octaveFactorSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.pitchBendSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc1AttackSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc1DecaySeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc1SustainSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc1ReleaseSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc2AttackSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc2DecaySeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc2SustainSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc2ReleaseSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.maxSpreadSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.gridSizeSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);

        this.osc1WaveSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc2WaveSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc1WaveControlSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.osc2WaveControlSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.delayLevelSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.delayTimeSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.delayFeedbackSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        this.settingsButton = (Button) findViewById(R.id.toggleSettingsButton);
        this.recButton = (Button)findViewById(R.id.recButton);
        this.playButton = (Button)findViewById(R.id.playButton);
        this.loopMeter = findViewById(R.id.loopMeter);
        this.loopClearButton = findViewById(R.id.loopClearButton);
        this.recButton.setSelected(record);
        this.playButton.setSelected(play);
        this.recButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!record) {
                    record = true;
                    recordStartTime = System.currentTimeMillis();
                    play = false;
                    playButton.setSelected(false);
                    engine.setRecord(true);
                    recButton.setSelected(true);
                } else {
                    record = false;
                    engine.setRecord(false);
                    recButton.setSelected(false);
                }
            }
        });
        this.playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!play) {
                    if (!engine.hasLoop()) {
                        Toast.makeText(MainActivity.this,
                                "Record a loop first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (record) {
                        record = false;
                        recButton.setSelected(false);
                        engine.setRecord(false);
                    }
                    play = true;
                    playStartTime = System.currentTimeMillis();
                    engine.setPlay(true);
                    playButton.setSelected(true);
                } else {
                    play = false;
                    engine.setPlay(false);
                    playButton.setSelected(false);
                }
            }
        });
        this.loopClearButton.setOnClickListener(v -> clearLoop());
        this.optionsScrollView = findViewById(R.id.optionsScrollView);
        this.menuView = findViewById(R.id.menu);
        this.settingsPanel = findViewById(R.id.settingsPanel);
        this.settingsPanel.setClipToOutline(true);
        this.settingsPanel.setVisibility(View.GONE);
        this.menuView.setVisibility(View.VISIBLE);
        this.settingsButton.setOnClickListener(v -> showSettings());
        findViewById(R.id.closeSettingsButton).setOnClickListener(v -> hideSettings());
        redToggleButton.setChecked(red);
        legatoToggleButton.setChecked(legato);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            setupMidi(R.id.spinnerMidiDevice,R.id.spinnerInputMidiDevice);
        } else {
            Toast.makeText(MainActivity.this,
                    "MIDI not supported!", Toast.LENGTH_LONG)
                    .show();
        }

        setupRtpMidi();

        modMatrix = new ModMatrixController(this, engine, prefs);
        modMatrix.attach();

        screenUpdater = new Runnable() {
            @Override
            public void run() {
                if (engine == null) {
                    return;
                }
                /*
                long now = new java.util.Date().getTime();
                if ((touchPoints == 0) && (now - lastTouchEventTime) > 60000) {
                    for (int i = 0; i < 128; i++) {
                        engine.sendMidiNoteOff(1, i, 0);

                    }
                    lastTouchEventTime = now;
                }*/
                float[] waveform = engine.getWaveform();
                if (waveform != null) {
                    scope.setData(waveform);
                    if (spectrumAnalyzer == null) {
                        spectrumAnalyzer = new SpectrumAnalyzer();
                        spectrumBars = new float[SpectrumAnalyzer.BAR_COUNT];
                    }
                    if ((spectrumFrame++ & 1) == 0) {
                        spectrumAnalyzer.analyze(waveform,
                                engine.getSampleRate(), spectrumBars);
                        scope.setSpectrum(spectrumBars);
                    }
                }
                updateDspMeter();
                updateLoopUi();
                scope.invalidate();
                mHandler.postDelayed(screenUpdater, updateInterval);
            }
        };

        mHandler.postDelayed(screenUpdater, updateInterval);

    }

    public String midiNoteToString(int note) {
        note = note % tet;
        if (tet == 12) {
            switch (note) {
                case 0:
                    return "C";

                case 1:
                    return "C#";
                case 2:
                    return "D";
                case 3:
                    return "D#";
                case 4:
                    return "E";
                case 5:
                    return "F";
                case 6:
                    return "F#";
                case 7:
                    return "G";
                case 8:
                    return "G#";
                case 9:
                    return "A";
                case 10:
                    return "A#";
                case 11:
                    return "B";
            }
        } else {
            return "T" + (note % tet) + "_" + (note / tet);
        }
        return "";
    }

    public void applySettings() {
        String rootNote = (String) rootNoteSpinner.getSelectedItem();
        int note = 0;
        if (rootNote != null) {
            if (rootNote.length() > 0) {
                if (rootNote.indexOf("C#") >= 0) {
                    note = 1;
                } else if (rootNote.indexOf("C") >= 0) {
                    note = 0;
                } else if (rootNote.indexOf("D#") >= 0) {
                    note = 3;
                } else if (rootNote.indexOf("D") >= 0) {
                    note = 2;
                } else if (rootNote.indexOf("E") >= 0) {
                    note = 4;
                } else if (rootNote.indexOf("F#") >= 0) {
                    note = 6;
                } else if (rootNote.indexOf("F") >= 0) {
                    note = 5;
                } else if (rootNote.indexOf("G#") >= 0) {
                    note = 8;
                } else if (rootNote.indexOf("G") >= 0) {
                    note = 7;
                } else if (rootNote.indexOf("A#") >= 0) {
                    note = 10;
                } else if (rootNote.indexOf("A") >= 0) {
                    note = 9;
                } else if (rootNote.indexOf("B") >= 0) {
                    note = 11;
                }


            }
            String numString = rootNote.substring(0, rootNote.indexOf("("));
            int octave = Integer.parseInt(numString.substring(numString.length() - 1));

            this.rootNote = 12 * octave + note;
            rootNoteStr = midiNoteToString(this.rootNote);
            prefs.writeInt("rootNote", this.rootNote);
            updatePitchMapper();
        }
    }

    private void scheduleSaveSynthState() {
        if (mHandler == null) {
            return;
        }
        mHandler.removeCallbacks(saveSynthStateRunnable);
        mHandler.postDelayed(saveSynthStateRunnable, 500);
    }

    private void saveSynthStateNow() {
        if (engine == null || prefs == null ||
                engine.getEngineMode() == SynthEngine.ENGINE_LEGACY) {
            return;
        }
        String state = engine.saveYawnState();
        if (state != null) {
            prefs.writeString(PREF_YAWN_STATE, state);
        }
    }

    private void restoreSynthState() {
        if (engine == null || prefs == null ||
                engine.getEngineMode() == SynthEngine.ENGINE_LEGACY) {
            return;
        }
        String state = prefs.readString(PREF_YAWN_STATE, "");
        if (state == null || state.isEmpty()) {
            return;
        }
        String error = engine.restoreYawnState(state);
        if (error != null) {
            prefs.writeString(PREF_YAWN_STATE, "");
            return;
        }
        yawnTempo = engine.getYawnTempo();
        yawnMasterVolume = engine.getYawnMasterVolume();
        prefs.writeInt("yawnTempo", (int) (yawnTempo * 10));
        prefs.writeInt("yawnMasterVolume", (int) (yawnMasterVolume * 1000));
    }

    private void retriggerActiveTouches() {
        if (engine == null) {
            return;
        }
        for (int i = 0; i < touchVoices.size(); i++) {
            TouchVoice voice = touchVoices.valueAt(i);
            if (voice == null) {
                continue;
            }
            engine.sendMidiNoteOn(0, voice.anchorNote, voice.velocity);
            engine.sendMidiNotePitch(0, voice.anchorNote,
                    (float) TouchModulation.scaledBend(
                            voice.expression.bendSemitones(), pitchBendAmount));
            engine.sendMidiNoteSpread(0, voice.anchorNote,
                    63 + voice.lastSpread);
            engine.selectWaveform(0, 0, voice.anchorNote, voice.waveform1);
            engine.selectWaveform(0, 1, voice.anchorNote, voice.waveform2);
        }
    }

    private void updateLoopUi() {
        if (engine == null || loopMeter == null) {
            return;
        }
        final boolean nativeRecording = engine.isLoopRecording();
        final boolean nativePlaying = engine.isLoopPlaying();
        final boolean loopExists = engine.hasLoop();
        final long now = System.currentTimeMillis();
        if (record && !nativeRecording && now - recordStartTime > 500) {
            record = false;
            recButton.setSelected(false);
        }
        if (play && !nativePlaying && now - playStartTime > 500) {
            play = false;
            playButton.setSelected(false);
        }
        loopMeter.setLoopState(nativeRecording, nativePlaying, loopExists,
                engine.getLoopRecordProgress(),
                engine.getLoopPlayProgress());
        if (loopClearButton != null) {
            loopClearButton.setAlpha(loopExists || nativeRecording ? 1.0f : 0.4f);
        }
    }

    private void clearLoop() {
        if (engine != null) {
            engine.clearLoop();
        }
        record = false;
        play = false;
        if (recButton != null) {
            recButton.setSelected(false);
        }
        if (playButton != null) {
            playButton.setSelected(false);
        }
        if (loopMeter != null) {
            loopMeter.setLoopState(false, false, false, 0.0f, 0.0f);
        }
    }

    private void applySafetySettings() {
        if (engine == null) {
            return;
        }
        engine.setSafetyLimiter(safetyLimiter);
        engine.setPolyphonyLeveling(polyphonyLeveling);
    }

    private void updateDspMeter() {
        if (dspLoadText == null || engine == null) {
            return;
        }
        if ((dspUiCounter++ % 5) != 0) {
            return;
        }
        float load = Math.min(999.9f, engine.getDspLoad() * 100.0f);
        float peak = Math.min(999.9f, engine.getDspPeak() * 100.0f);
        dspLoadText.setText(String.format(Locale.US,
                "DSP %5.1f%%  pk %5.1f%%", load, peak));
        if (load > 80.0f) {
            dspLoadText.setTextColor(0xFFFF5A6E);
        } else if (load > 60.0f) {
            dspLoadText.setTextColor(0xFFFFB35C);
        } else {
            dspLoadText.setTextColor(0xFF5CFFC0);
        }
    }

    private void updateGridSizeLabel() {
        if (gridSizeTextView != null) {
            gridSizeTextView.setText("Grid size: " + xNoteScale + " px / note");
        }
    }

    private void setupScaleSpinner() {
        scaleSpinner = (Spinner) findViewById(R.id.scaleSpinner);
        editScaleButton = (Button) findViewById(R.id.editScaleButton);
        editScaleButton.setOnClickListener(v -> showScaleEditor());
        scaleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (updatingScaleSpinner || position < 0
                        || position >= scaleOptions.size()) {
                    return;
                }
                currentScaleDefinition = scaleOptions.get(position);
                currentScaleId = currentScaleDefinition.id;
                prefs.writeString("currentScaleId", currentScaleId);
                registerLayout.ensureDegreeDefaults(
                        currentScaleDefinition.size());
                updatePitchMapper();
                refreshRegisterUi();
                updateScopeText();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        rebuildScaleSpinner();
    }

    private void rebuildScaleSpinner() {
        if (scaleSpinner == null || scaleStore == null) {
            return;
        }
        scaleOptions.clear();
        scaleOptions.addAll(scaleStore.scalesForTet(tet));
        int selected = 0;
        for (int i = 0; i < scaleOptions.size(); i++) {
            if (scaleOptions.get(i).id.equals(currentScaleId)) {
                selected = i;
                break;
            }
        }
        String[] names = new String[scaleOptions.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = scaleOptions.get(i).name;
        }
        updatingScaleSpinner = true;
        ArrayAdapter<String> adapter = spinnerAdapter(names);
        scaleSpinner.setAdapter(adapter);
        scaleSpinner.setSelection(selected, false);
        currentScaleDefinition = scaleOptions.get(selected);
        currentScaleId = currentScaleDefinition.id;
        prefs.writeString("currentScaleId", currentScaleId);
        updatingScaleSpinner = false;
        if (editScaleButton != null) {
            boolean enabled = tet == 12;
            editScaleButton.setEnabled(enabled);
            editScaleButton.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }

    private void showScaleEditor() {
        if (tet != 12 || currentScaleDefinition == null) {
            Toast.makeText(this, "Custom scales are available for TET 12",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean custom = !currentScaleDefinition.builtIn;
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        content.setPadding(padding, padding, padding, padding);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Scale name");
        nameInput.setSingleLine(true);
        nameInput.setText(custom ? currentScaleDefinition.name
                : "Copy of " + currentScaleDefinition.name);
        content.addView(nameInput);

        final boolean[] selected = new boolean[12];
        for (int degree : currentScaleDefinition.degrees) {
            if (degree >= 0 && degree < 12) {
                selected[degree] = true;
            }
        }
        selected[0] = true;
        final ToggleButton[] buttons = new ToggleButton[12];
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 4; col++) {
                final int degree = row * 4 + col;
                ToggleButton button = new ToggleButton(this);
                String noteLabel = noteName(MicrotonalPitchMapper.clampNote(
                        rootNote + degree));
                button.setTextOn(noteLabel);
                button.setTextOff(noteLabel);
                button.setText(noteLabel);
                button.setTextSize(10);
                button.setChecked(selected[degree]);
                button.setEnabled(degree != 0);
                button.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                button.setOnCheckedChangeListener((b, checked) ->
                        selected[degree] = checked || degree == 0);
                buttons[degree] = button;
                line.addView(button);
            }
            content.addView(line);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(custom ? "Edit TET12 scale" : "New TET12 scale")
                .setView(content)
                .setPositiveButton("Save", (dialog, which) -> {
                    selected[0] = true;
                    int count = 0;
                    for (boolean value : selected) {
                        if (value) count++;
                    }
                    int[] degrees = new int[count];
                    int index = 0;
                    for (int i = 0; i < selected.length; i++) {
                        if (selected[i]) degrees[index++] = i;
                    }
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) name = "Custom scale";
                    ScaleDefinition saved = scaleStore.saveCustom(
                            custom ? currentScaleDefinition.id : null,
                            name, degrees);
                    currentScaleId = saved.id;
                    rebuildScaleSpinner();
                    registerLayout.ensureDegreeDefaults(
                            currentScaleDefinition.size());
                    updatePitchMapper();
                    refreshRegisterUi();
                    updateScopeText();
                })
                .setNegativeButton("Cancel", null);
        if (custom) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                scaleStore.deleteCustom(currentScaleDefinition.id);
                currentScaleId = "";
                rebuildScaleSpinner();
                updatePitchMapper();
                refreshRegisterRows();
                updateScopeText();
            });
        }
        builder.show();
    }

    private String labelForCell(int cell, int zone) {
        if (pitchMapper == null || currentScaleDefinition == null
                || zone < 0 || zone >= registerLayout.count()) {
            return null;
        }
        int register = registerLayout.count() - 1 - zone;
        if (tet == 12) {
            double pitch = pitchForCell(cell, register);
            return noteName(MicrotonalPitchMapper.clampNote(
                    (int) Math.round(pitch)));
        }
        int degreeOffset = registerLayout.unit()
                == RegisterLayout.UNIT_SCALE_STEPS
                ? registerLayout.getDegreeOffset(register) : 0;
        int degree = Math.floorMod(cell + degreeOffset,
                currentScaleDefinition.size());
        return String.valueOf(degree + 1);
    }

    private double pitchForCell(int cell, int zone) {
        if (registerLayout.unit() == RegisterLayout.UNIT_SEMITONES) {
            return pitchMapper.pitchForCell(cell,
                    registerLayout.getSemitoneOffset(zone));
        }
        return pitchMapper.pitchForCell(cell + registerLayout.getDegreeOffset(zone),
                0);
    }

    private MicrotonalPitchMapper.Pitch pitchForTouch(float x, int zone) {
        if (registerLayout.unit() == RegisterLayout.UNIT_SEMITONES) {
            return pitchMapper.pitchAt(x, registerLayout.getSemitoneOffset(zone));
        }
        return pitchMapper.pitchAt(x, 0, registerLayout.getDegreeOffset(zone));
    }

    private void setupRegisterControls() {
        registerCountSeekBar = (SeekBar) findViewById(R.id.registerCountSeekBar);
        registerCountText = (TextView) findViewById(R.id.registerCountText);
        registerUnitSpinner = (Spinner) findViewById(R.id.registerUnitSpinner);
        registerPresetSpinner = (Spinner) findViewById(R.id.registerPresetSpinner);
        registerControls = (LinearLayout) findViewById(R.id.registerControls);
        if (registerCountSeekBar == null || registerControls == null) {
            return;
        }
        registerCountSeekBar.setMax(RegisterLayout.MAX_REGISTERS - 1);
        registerCountSeekBar.setProgress(registerLayout.count() - 1);
        registerCountSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                                                  boolean fromUser) {
                        if (!fromUser) return;
                        registerLayout.setCount(progress + 1);
                        registerLayout.save(prefs);
                        refreshRegisterUi();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

        registerUnitSpinner.setAdapter(spinnerAdapter(
                new String[] {"Semitones", "Scale steps"}));
        registerUnitSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                                               int position, long id) {
                        if (updatingRegisterUi) return;
                        registerLayout.setUnit(position == 1
                                        ? RegisterLayout.UNIT_SCALE_STEPS
                                        : RegisterLayout.UNIT_SEMITONES,
                                currentScaleDefinition.size());
                        registerLayout.save(prefs);
                        refreshRegisterRows();
                        if (scope != null) scope.invalidateNoteLabels();
                        updateScopeText();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        registerPresetSpinner.setAdapter(spinnerAdapter(
                new String[] {"Custom", "Octaves", "Power", "Major",
                        "Minor", "Guitar strings"}));
        registerPresetSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                                               int position, long id) {
                        if (updatingRegisterUi || position == 0) return;
                        registerLayout.applyPreset(position,
                                currentScaleDefinition.size());
                        registerLayout.save(prefs);
                        refreshRegisterUi();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
        refreshRegisterUi();
    }

    private void refreshRegisterUi() {
        if (registerControls == null) {
            return;
        }
        updatingRegisterUi = true;
        registerCountSeekBar.setProgress(registerLayout.count() - 1);
        registerCountText.setText("Registers: " + registerLayout.count());
        registerUnitSpinner.setSelection(registerLayout.unit(), false);
        registerPresetSpinner.setSelection(registerLayout.preset(), false);
        updatingRegisterUi = false;
        refreshRegisterRows();
        if (scope != null) {
            scope.setRegisterCount(registerLayout.count());
            scope.invalidateNoteLabels();
        }
        updateScopeText();
    }

    private void refreshRegisterRows() {
        if (registerControls == null) {
            return;
        }
        updatingRegisterUi = true;
        registerControls.removeAllViews();
        for (int i = registerLayout.count() - 1; i >= 0; i--) {
            final int register = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(4), dp(6), dp(4));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(4);
            row.setLayoutParams(rowParams);
            row.setBackgroundResource(R.drawable.bg_input);

            View swatch = new View(this);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                    dp(12), dp(26));
            swatchParams.setMarginEnd(dp(8));
            swatch.setLayoutParams(swatchParams);
            swatch.setBackgroundColor(RegisterLayout.colorFor(i));
            row.addView(swatch);

            TextView label = new TextView(this);
            label.setText("R" + (i + 1));
            label.setTextSize(11);
            label.setTextColor(getColor(R.color.text_primary));
            label.setTypeface(Typeface.MONOSPACE);
            label.setLayoutParams(new LinearLayout.LayoutParams(dp(38),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(label);

            Button minus = registerStepButton("−");
            minus.setOnClickListener(v -> {
                registerLayout.setOffset(register,
                        registerLayout.getOffset(register) - 1);
                registerLayout.save(prefs);
                refreshRegisterUi();
                if (scope != null) scope.invalidateNoteLabels();
            });
            row.addView(minus);

            TextView value = new TextView(this);
            value.setText(registerOffsetLabel(i));
            value.setTextSize(12);
            value.setTypeface(Typeface.MONOSPACE);
            value.setTextColor(getColor(R.color.accent));
            value.setGravity(Gravity.CENTER);
            value.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            row.addView(value);

            Button plus = registerStepButton("+");
            plus.setOnClickListener(v -> {
                registerLayout.setOffset(register,
                        registerLayout.getOffset(register) + 1);
                registerLayout.save(prefs);
                refreshRegisterUi();
                if (scope != null) scope.invalidateNoteLabels();
            });
            row.addView(plus);
            registerControls.addView(row);
        }
        updatingRegisterUi = false;
    }

    private String registerOffsetLabel(int register) {
        int value = registerLayout.getOffset(register);
        String suffix = registerLayout.unit() == RegisterLayout.UNIT_SEMITONES
                ? " st" : " steps";
        return (value >= 0 ? "+" : "") + value + suffix;
    }

    private Button registerStepButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(getColorStateList(R.color.button_text_selector));
        button.setBackgroundResource(R.drawable.bg_button_secondary);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(34));
        button.setMinimumHeight(dp(34));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private void updatePitchMapper() {
        if (currentScaleDefinition == null) {
            return;
        }
        pitchMapper = new MicrotonalPitchMapper(rootNote,
                currentScaleDefinition, tune, octaveFactor, xNoteScale);
        if (scope != null) {
            scope.invalidateNoteLabels();
        }
    }

    private void setupPerformanceTouch() {
        if (performanceArea == null) {
            return;
        }
        performanceArea.setOnTouchListener((v, event) -> {
            handlePerformanceTouch(event);
            return true;
        });
    }

    private boolean handlePerformanceTouch(MotionEvent event) {
        if (engine != null) {
            engine.resumeAudio();
        }
        lastTouchEventTime = System.currentTimeMillis();
        final float height = touchSurfaceHeight();
        if (pitchMapper == null) {
            updatePitchMapper();
        }
        final int action = event.getActionMasked();
        final int actionIndex = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                startTouch(event, actionIndex, height);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    TouchVoice voice = touchVoices.get(event.getPointerId(i));
                    if (voice != null) {
                        updateTouch(voice, event, i, height);
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                stopTouch(event.getPointerId(actionIndex), true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopAllTouches(true);
                break;
            default:
                break;
        }
        touchPoints = touchVoices.size();
        updateScopeText();
        return true;
    }

    private float touchSurfaceHeight() {
        float height = scope != null ? scope.getHeight() : 0.0f;
        if (height <= 0.0f && performanceArea != null) {
            height = performanceArea.getHeight();
        }
        if (height <= 0.0f) {
            height = getResources().getDisplayMetrics().heightPixels;
        }
        return height;
    }

    private float touchSurfaceWidth() {
        float width = scope != null ? scope.getWidth() : 0.0f;
        if (width <= 0.0f && performanceArea != null) {
            width = performanceArea.getWidth();
        }
        if (width <= 0.0f) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        return width;
    }

    private void publishTouchModulation(TouchVoice voice, float x, float y) {
        if (engine == null || voice == null) {
            return;
        }
        final float width = touchSurfaceWidth();
        final float height = touchSurfaceHeight();
        engine.setYawnModSource(SensorModulation.TOUCH_X,
                TouchModulation.normalizedX(x, width));
        engine.setYawnModSource(SensorModulation.TOUCH_Y,
                TouchModulation.normalizedY(y, height));
        engine.setYawnModSource(SensorModulation.TOUCH_DELTA_X,
                TouchModulation.relativeX(x, voice.startX, width));
        engine.setYawnModSource(SensorModulation.TOUCH_DELTA_Y,
                TouchModulation.relativeY(y, voice.startY, height));
    }

    private void resetTouchModulation() {
        if (engine == null) {
            return;
        }
        engine.setYawnModSource(SensorModulation.TOUCH_X, 0.0f);
        engine.setYawnModSource(SensorModulation.TOUCH_Y, 0.0f);
        engine.setYawnModSource(SensorModulation.TOUCH_DELTA_X, 0.0f);
        engine.setYawnModSource(SensorModulation.TOUCH_DELTA_Y, 0.0f);
    }

    private void sendTouchPitch(TouchVoice voice, int note,
                                double bendSemitones) {
        double bend = TouchModulation.scaledBend(bendSemitones,
                pitchBendAmount);
        engine.sendMidiNotePitch(0, note, (float) bend);
        midiPitchBend(voice.channel, pitchBend14(bend));
    }

    private void refreshActiveTouchPitch() {
        for (int i = 0; i < touchVoices.size(); i++) {
            TouchVoice voice = touchVoices.valueAt(i);
            sendTouchPitch(voice, voice.anchorNote,
                    voice.expression.bendSemitones());
        }
        updateScopeText();
    }

    private void startTouch(MotionEvent event, int index, float height) {
        final int id = event.getPointerId(index);
        if (touchVoices.get(id) != null) {
            stopTouch(id, false);
        }
        final float x = event.getX(index);
        final float y = event.getY(index);
        final float expression = rawTouchExpression(event, index);
        final int velocity = velocityForExpression(expression);
        final int channel = mpeChannelAllocator.allocate();
        final int register = registerLayout.registerAt(y, height);
        final MicrotonalPitchMapper.Pitch pitch = pitchForTouch(x, register);

        TouchVoice voice = new TouchVoice(id, channel);
        voice.register = register;
        voice.velocity = velocity;
        voice.pressure = expression >= 0.0f ? expression : 0.5f;
        int[] waveforms = waveformsForTouch(y, height);
        voice.waveform1 = waveforms[0];
        voice.waveform2 = waveforms[1];
        MicrotonalVoice.Transition transition = voice.expression.start(pitch);
        voice.anchorNote = transition.anchorNote;
        voice.lastSpread = (int) spreadForTouch(x);
        voice.startX = x;
        voice.startY = y;
        voice.lastX = x;
        voice.lastY = y;
        touchVoices.put(id, voice);

        engine.sendMidiNoteOn(0, transition.noteOn, velocity);
        midiNoteOn(channel, transition.noteOn, velocity);
        sendTouchPitch(voice, transition.noteOn, transition.bendSemitones);
        sendTouchPressure(voice);
        selectWaveformForVoice(voice);
        publishTouchModulation(voice, x, y);
        scope.setMarker("" + id, x, y, noteName(transition.noteOn)
                + " " + velocity, register);
        vel = velocity;
    }

    private void updateTouch(TouchVoice voice, MotionEvent event, int index,
                             float height) {
        final float x = event.getX(index);
        final float y = event.getY(index);
        final float expression = rawTouchExpression(event, index);
        final int velocity = velocityForExpression(expression);
        final int register = registerLayout.registerAt(y, height);
        final MicrotonalPitchMapper.Pitch pitch = pitchForTouch(x, register);
        voice.register = register;
        final MicrotonalVoice.Transition transition =
                voice.expression.update(pitch,
                        legato && pitchBendAmount > 0.001f);

        if (transition.noteOff >= 0) {
            engine.sendMidiNoteOff(0, transition.noteOff, 0);
            midiNoteOff(voice.channel, transition.noteOff, 0);
        }
        if (transition.noteOn >= 0) {
            engine.sendMidiNoteOn(0, transition.noteOn, velocity);
            midiNoteOn(voice.channel, transition.noteOn, velocity);
        }

        voice.anchorNote = transition.anchorNote;
        voice.velocity = velocity;
        voice.pressure = TouchExpression.smooth(voice.pressure, expression);
        sendTouchPitch(voice, transition.anchorNote, transition.bendSemitones);
        sendTouchPressure(voice);

        int[] waveforms = waveformsForTouch(y, height);
        voice.waveform1 = waveforms[0];
        voice.waveform2 = waveforms[1];
        selectWaveformForVoice(voice);

        voice.lastSpread = (int) spreadForTouch(x);
        engine.sendMidiNoteSpread(0, voice.anchorNote,
                63 + voice.lastSpread);
        voice.lastX = x;
        voice.lastY = y;
        publishTouchModulation(voice, x, y);
        double playedPitch = voice.anchorNote + TouchModulation.scaledBend(
                transition.bendSemitones, pitchBendAmount);
        int displayNote = Math.max(0, Math.min(127,
                (int) Math.round(playedPitch)));
        scope.setMarker("" + voice.pointerId, x, y,
                noteName(displayNote) + " " + velocity, register);
        vel = velocity;
    }

    private void stopTouch(int id, boolean unsetMarker) {
        TouchVoice voice = touchVoices.get(id);
        if (voice == null) {
            return;
        }
        MicrotonalVoice.Transition transition = voice.expression.stop();
        if (transition.noteOff >= 0) {
            engine.sendMidiNoteOff(0, transition.noteOff, 0);
            midiNoteOff(voice.channel, transition.noteOff, 0);
        }
        midiChannelPressure(voice.channel, 0);
        mpeChannelAllocator.release(voice.channel);
        touchVoices.remove(id);
        if (touchVoices.size() == 0) {
            resetTouchModulation();
        } else {
            TouchVoice remaining = touchVoices.valueAt(0);
            publishTouchModulation(remaining, remaining.lastX, remaining.lastY);
        }
        if (unsetMarker) {
            scope.unsetMarker("" + id);
        }
    }

    private void stopAllTouches(boolean unsetMarkers) {
        while (touchVoices.size() > 0) {
            int id = touchVoices.keyAt(0);
            stopTouch(id, unsetMarkers);
        }
        resetTouchModulation();
        if (mpeChannelAllocator != null) {
            mpeChannelAllocator.reset();
        }
    }

    private void selectWaveformForVoice(TouchVoice voice) {
        engine.selectWaveform(0, 0, voice.anchorNote, voice.waveform1);
        engine.selectWaveform(0, 1, voice.anchorNote, voice.waveform2);
    }

    private int[] waveformsForTouch(float y, float height) {
        int factor = Math.max(1, (int) height / 3);
        int wi = (int) y % factor;
        int w1 = normalizeWave(osc1Wave + (wi * osc1WaveControl) / factor);
        int w2 = normalizeWave(osc2Wave + (wi * osc2WaveControl) / factor);
        return new int[] {w1, w2};
    }

    private static int normalizeWave(int wave) {
        int value = wave % 256;
        return value < 0 ? value + 256 : value;
    }

    private double spreadForTouch(float x) {
        double withinCell = x % xNoteScale;
        if (withinCell < 0) {
            withinCell += xNoteScale;
        }
        int oscdist = -((int) (((xNoteScale - withinCell) / xNoteScale) * 127) - 63);
        return (maxSpread / 127.0) * oscdist;
    }

    private float rawTouchExpression(MotionEvent event, int index) {
        final InputDevice device = event.getDevice();
        if (device != null) {
            if (pressureSupported == null) {
                InputDevice.MotionRange range =
                        device.getMotionRange(MotionEvent.AXIS_PRESSURE);
                pressureSupported = range != null && range.getRange() > 0.0f;
            }
            if (sizeSupported == null) {
                InputDevice.MotionRange range =
                        device.getMotionRange(MotionEvent.AXIS_SIZE);
                sizeSupported = range != null && range.getRange() > 0.0f;
            }
        }
        if (Boolean.TRUE.equals(pressureSupported)) {
            return Math.max(0.0f, Math.min(1.0f, event.getPressure(index)));
        }
        if (Boolean.TRUE.equals(sizeSupported)) {
            return Math.max(0.0f, Math.min(1.0f, event.getSize(index)));
        }
        return -1.0f;
    }

    private static int velocityForExpression(float expression) {
        return TouchExpression.velocity(expression);
    }

    private void sendTouchPressure(TouchVoice voice) {
        int pressure = TouchExpression.midiPressure(voice.pressure);
        if (pressure >= 0) {
            midiChannelPressure(voice.channel, pressure);
        }
    }

    private static int pitchBend14(double bendSemitones) {
        int value = 8192 + (int) Math.round(bendSemitones /
                MicrotonalPitchMapper.BEND_RANGE_SEMITONES * 8192.0);
        return Math.max(0, Math.min(16383, value));
    }

    private void midiPitchBend(int channel, int value14) {
        midiCommand(MidiConstants.STATUS_PITCH_BEND | channel,
                value14 & 0x7F, (value14 >> 7) & 0x7F);
    }

    private void midiChannelPressure(int channel, int pressure) {
        midiCommand(MidiConstants.STATUS_CHANNEL_PRESSURE | channel,
                Math.max(0, Math.min(127, pressure)));
    }

    private void updateScopeText() {
        StringBuilder text = new StringBuilder();
        if (tet == 12) {
            text.append(rootNoteStr).append(' ')
                    .append(currentScaleDefinition.name);
        } else {
            text.append("TET ").append(tet).append(' ')
                    .append(currentScaleDefinition.name);
        }
        for (int i = 0; i < touchVoices.size(); i++) {
            TouchVoice voice = touchVoices.valueAt(i);
            double target = voice.anchorNote + TouchModulation.scaledBend(
                    voice.expression.bendSemitones(), pitchBendAmount);
            int displayNote = Math.max(0, Math.min(127,
                    (int) Math.round(target)));
            text.append("  ").append(noteName(displayNote));
            double cents = (target - displayNote) * 100.0;
            if (Math.abs(cents) >= 1.0) {
                text.append(String.format(Locale.US, "%+.0fc", cents));
            }
            text.append('(').append(voice.velocity).append(')');
        }
        if (legato) {
            text.append("  L");
        }
        if (performanceInfoText != null) {
            performanceInfoText.setText(text.toString());
        }
    }

    private static String noteName(int note) {
        String[] names = {"C", "C#", "D", "D#", "E", "F",
                "F#", "G", "G#", "A", "A#", "B"};
        int clamped = Math.max(0, Math.min(127, note));
        return names[clamped % 12] + (clamped / 12);
    }

    private static final class TouchVoice {
        final int pointerId;
        final int channel;
        final MicrotonalVoice expression = new MicrotonalVoice();
        int velocity;
        float pressure = 0.5f;
        int waveform1;
        int waveform2;
        int anchorNote;
        int lastSpread;
        int register;
        float startX;
        float startY;
        float lastX;
        float lastY;

        TouchVoice(int pointerId, int channel) {
            this.pointerId = pointerId;
            this.channel = channel;
        }
    }

    public void logMidi(byte[] data) {
    }

    private class PortsConnectedListener
            implements org.bokontep.midi.MidiPortConnector.OnPortsConnectedListener {
        @Override
        public void onPortsConnected(final MidiDevice.MidiConnection connection) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (connection == null) {
                        Toast.makeText(MainActivity.this,
                                "PORT BUSY", Toast.LENGTH_LONG)
                                .show();
                        midiPortSelector.clearSelection();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "PORT OPENED!", Toast.LENGTH_LONG)
                                .show();
                    }
                }
            });
        }
    }

    private void setupMidi(int spinnerID, int spinnerID2) {
        // Setup MIDI
        midiManager = (MidiManager) getSystemService(MIDI_SERVICE);

        MidiDeviceInfo synthInfo = MidiTools.findDevice(midiManager, "Bokontep",
                "Volna");
        int portIndex = 0;
        midiPortSelector = new MidiOutputPortConnectionSelector(midiManager, this,
                spinnerID, synthInfo, portIndex);
        midiPortSelector.setConnectedListener(new MidiPortConnector.OnPortsConnectedListener() {
            @Override
            public void onPortsConnected(final MidiDevice.MidiConnection connection) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (connection == null) {
                            Toast.makeText(MainActivity.this,
                                    "Port busy!", Toast.LENGTH_LONG)
                                    .show();
                            midiPortSelector.clearSelection();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Port opened!", Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                });
            }
        });
        midiReceiver = new VolnaMidiReceiver(engine);

        VolnaMidiDeviceService.setMidiReceiver(midiReceiver);
        midiInputPortSelector = new MidiInputPortSelector(midiManager,this,spinnerID2);
    }


    private void midiNoteOff(int channel, int pitch, int velocity) {
        midiCommand(MidiConstants.STATUS_NOTE_OFF | channel, pitch, velocity);
    }

    private void midiNoteOn(int channel, int pitch, int velocity) {
        midiCommand(MidiConstants.STATUS_NOTE_ON | channel, pitch, velocity);
    }
    private void midiSendCC(int channel, int cc, int data)
    {
        midiCommand(MidiConstants.STATUS_CONTROL_CHANGE | channel, cc, data);
    }
    private void midiCommand(int status, int data1, int data2) {
        mByteBuffer[0] = (byte) status;
        mByteBuffer[1] = (byte) data1;
        mByteBuffer[2] = (byte) data2;
        long now = System.nanoTime();
        midiSend(mByteBuffer, 3, now);
    }

    private void midiCommand(int status, int data1) {
        mByteBuffer[0] = (byte) status;
        mByteBuffer[1] = (byte) data1;
        long now = System.nanoTime();
        midiSend(mByteBuffer, 2, now);
    }
    private void midiSend(byte[] buffer, int count, long timestamp)
    {
        if (rtpMidiClient != null) {
            rtpMidiClient.send(buffer, count);
        }
        try {
            if (this.midiInputPortSelector != null) {
                MidiReceiver receiver = this.midiInputPortSelector.getReceiver();
                if(receiver!=null) {
                    receiver.send(buffer, 0, count, timestamp);
                }
            }
        }
        catch (IOException e) {

        }

    }

    private void setupRtpMidi() {
        rtpMidiClient = new RtpMidiClient();
        rtpMidiHostEditText = (EditText) findViewById(R.id.rtpMidiHostEditText);
        rtpMidiPortEditText = (EditText) findViewById(R.id.rtpMidiPortEditText);
        rtpMidiToggleButton = (ToggleButton) findViewById(R.id.rtpMidiToggleButton);
        rtpMidiStatusText = (TextView) findViewById(R.id.rtpMidiStatusText);

        rtpMidiHostEditText.setText(prefs.readString("rtpMidiHost", ""));
        rtpMidiPortEditText.setText(String.valueOf(
                prefs.readInt("rtpMidiPort", 5004)));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                prefs.writeString("rtpMidiHost",
                        rtpMidiHostEditText.getText().toString().trim());
                int port = 5004;
                try {
                    port = Integer.parseInt(
                            rtpMidiPortEditText.getText().toString().trim());
                } catch (NumberFormatException ignored) {
                }
                prefs.writeInt("rtpMidiPort", port);
                mHandler.removeCallbacks(rtpMidiRestart);
                mHandler.postDelayed(rtpMidiRestart, 700);
            }
        };
        rtpMidiHostEditText.addTextChangedListener(watcher);
        rtpMidiPortEditText.addTextChangedListener(watcher);

        boolean enabled = prefs.readInt("rtpMidiEnabled", 0) == 1;
        rtpMidiToggleButton.setChecked(enabled);
        rtpMidiToggleButton.setOnCheckedChangeListener(
                (button, checked) -> {
                    prefs.writeInt("rtpMidiEnabled", checked ? 1 : 0);
                    if (checked) {
                        startRtpMidi();
                    } else {
                        stopRtpMidi();
                    }
                });
        if (enabled && !rtpMidiHostEditText.getText().toString().trim().isEmpty()) {
            startRtpMidi();
        }
    }

    private void startRtpMidi() {
        if (rtpMidiClient == null) {
            return;
        }
        String host = rtpMidiHostEditText.getText().toString().trim();
        int port = 5004;
        try {
            port = Integer.parseInt(
                    rtpMidiPortEditText.getText().toString().trim());
        } catch (NumberFormatException ignored) {
        }
        boolean started = rtpMidiClient.start(host, port, (message, error) ->
                runOnUiThread(() -> {
                    if (rtpMidiStatusText == null) {
                        return;
                    }
                    rtpMidiStatusText.setText(message);
                    rtpMidiStatusText.setTextColor(error ? Color.RED : Color.GREEN);
                }));
        if (started) {
            sendMpeSetup();
        }
    }

    /** Tell external MPE receivers our member-channel pitch range. */
    private void sendMpeSetup() {
        // Lower zone with 15 member channels (MPE Configuration Message).
        midiSendCC(0, 101, 0);
        midiSendCC(0, 100, 6);
        midiSendCC(0, 6, 15);
        midiSendCC(0, 101, 127);
        midiSendCC(0, 100, 127);
        for (int channel = 1; channel <= 15; channel++) {
            // Pitch Bend Sensitivity = 48 semitones, 0 cents.
            midiSendCC(channel, 101, 0);
            midiSendCC(channel, 100, 0);
            midiSendCC(channel, 6, 48);
            midiSendCC(channel, 38, 0);
            midiSendCC(channel, 101, 127);
            midiSendCC(channel, 100, 127);
        }
    }

    private void stopRtpMidi() {
        if (rtpMidiClient != null) {
            rtpMidiClient.stop();
        }
        if (rtpMidiStatusText != null) {
            rtpMidiStatusText.setText("RTP-MIDI off");
            rtpMidiStatusText.setTextColor(Color.GRAY);
        }
    }

    private void onWavetableFileSelected(Uri uri) {
        if (uri == null) {
            return;
        }
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IOException("Could not open the selected file");
                }
                byte[] bytes = readAll(in, VwtWavetable.MAX_FILE_BYTES);
                VwtWavetable table = VwtWavetable.parse(bytes);
                float[] bank = table.toVolnaBank(256, 256, 0.8f);
                runOnUiThread(() -> applyImportedWavetable(
                        table, bank, bytes, displayNameFromUri(uri), true));
            } catch (Exception e) {
                final String message = e.getMessage() == null
                        ? "Wavetable import failed"
                        : e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        message, Toast.LENGTH_LONG).show());
            }
        }, "wavetable-import").start();
    }

    private void restoreSavedWavetable() {
        if (!wavetableStore.hasStoredWavetable()) {
            customWavetableText.setText("Factory wavetables");
            if (engine.getEngineMode() == SynthEngine.ENGINE_YAWN_WAVETABLE &&
                    engine.getYawnInstrumentParamValue(0) > 4.5f) {
                engine.setYawnInstrumentParam(0, 0.0f);
            }
            return;
        }
        new Thread(() -> {
            final byte[] bytes = wavetableStore.load();
            final String source = wavetableStore.sourceName();
            try {
                if (bytes == null) {
                    throw new IOException("Stored wavetable is missing");
                }
                VwtWavetable table = VwtWavetable.parse(bytes);
                float[] bank = table.toVolnaBank(256, 256, 0.8f);
                runOnUiThread(() -> applyImportedWavetable(
                        table, bank, bytes, source, false));
            } catch (Exception e) {
                wavetableStore.clear();
                runOnUiThread(() -> {
                    if (customWavetableText != null) {
                        customWavetableText.setText("Factory wavetables");
                    }
                    Toast.makeText(MainActivity.this,
                            "Could not restore custom wavetable; using factory bank",
                            Toast.LENGTH_LONG).show();
                });
            }
        }, "wavetable-restore").start();
    }

    private void applyImportedWavetable(VwtWavetable table, float[] bank,
                                        byte[] bytes, String sourceName,
                                        boolean persist) {
        if (engine == null || isFinishing()) {
            return;
        }
        if (persist) {
            try {
                wavetableStore.save(bytes, table.name, sourceName);
            } catch (IOException e) {
                Toast.makeText(this, "Could not save the wavetable",
                        Toast.LENGTH_LONG).show();
            }
        }

        final int mode = engine.getEngineMode();
        if (mode == SynthEngine.ENGINE_LEGACY) {
            if (engine.setWavetableBank(bank) != 0) {
                Toast.makeText(this, "The synth rejected the wavetable",
                        Toast.LENGTH_LONG).show();
                return;
            }
        } else if (mode == SynthEngine.ENGINE_YAWN_WAVETABLE) {
            String error = engine.loadYawnWavetable(wavetableStore.filePath());
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            if (persist) {
                engine.switchEngine(SynthEngine.ENGINE_YAWN_WAVETABLE);
                prefs.writeInt("engineMode", SynthEngine.ENGINE_YAWN_WAVETABLE);
                setEngineSpinnerSilently(SynthEngine.ENGINE_YAWN_WAVETABLE);
                rebuildYawnControls();
                String error = engine.loadYawnWavetable(wavetableStore.filePath());
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }

        updateWavetableViews();
        if (customWavetableText != null) {
            customWavetableText.setText("Custom: " + table.name + " ("
                    + table.frameCount + " frames, " + table.frameSize
                    + " samples)");
        }
        rebuildYawnControls();
        scheduleSaveSynthState();
        Toast.makeText(this, "Loaded wavetable: " + table.name,
                Toast.LENGTH_SHORT).show();
    }

    private void restoreFactoryWavetable(String message) {
        if (engine == null) {
            return;
        }
        final int mode = engine.getEngineMode();
        if (mode == SynthEngine.ENGINE_LEGACY) {
            engine.resetWavetableBank();
        } else {
            if (mode != SynthEngine.ENGINE_YAWN_WAVETABLE) {
                engine.switchEngine(SynthEngine.ENGINE_YAWN_WAVETABLE);
                prefs.writeInt("engineMode", SynthEngine.ENGINE_YAWN_WAVETABLE);
                setEngineSpinnerSilently(SynthEngine.ENGINE_YAWN_WAVETABLE);
            }
            engine.resetYawnWavetable();
        }
        if (wavetableStore != null) {
            wavetableStore.clear();
        }
        updateWavetableViews();
        rebuildYawnControls();
        if (customWavetableText != null) {
            customWavetableText.setText("Factory wavetables");
        }
        scheduleSaveSynthState();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateWavetableViews() {
        if (engine == null) {
            return;
        }
        if (engine.getEngineMode() == SynthEngine.ENGINE_LEGACY) {
            osc1WaveDisplay.setData(engine.getWavetable(osc1Wave));
            osc2WaveDisplay.setData(engine.getWavetable(osc2Wave));
        } else {
            osc1WaveDisplay.setData(engine.getYawnWavetableFrame(
                    osc1Wave / 255.0f));
            osc2WaveDisplay.setData(engine.getYawnWavetableFrame(
                    osc2Wave / 255.0f));
        }
        osc1WaveDisplay.invalidate();
        osc2WaveDisplay.invalidate();
    }

    private void setEngineSpinnerSilently(int mode) {
        if (engineSpinner == null) {
            return;
        }
        AdapterView.OnItemSelectedListener listener =
                engineSpinner.getOnItemSelectedListener();
        engineSpinner.setOnItemSelectedListener(null);
        engineSpinner.setSelection(mode);
        engineSpinner.setOnItemSelectedListener(listener);
    }

    private void rebuildYawnControls() {
        if (soundControls == null || fxControls == null || engine == null) {
            return;
        }
        soundControls.removeAllViews();
        fxControls.removeAllViews();
        final int mode = engine.getEngineMode();
        if (mode == SynthEngine.ENGINE_LEGACY) {
            addInfoText(soundControls,
                    "Classic oscillator settings are below.");
            addInfoText(fxControls,
                    "YAWN effect chains are unavailable in Volna Classic.");
            midiFxSection = null;
            audioFxSection = null;
            return;
        }
        soundControls.setVisibility(View.VISIBLE);
        fxControls.setVisibility(View.VISIBLE);

        CollapsibleSection instrumentSection = new CollapsibleSection(this,
                "Instrument: " + engine.getYawnInstrumentName(mode - 1), true);
        int count = engine.getYawnInstrumentParamCount();
        for (int i = 0; i < count; i++) {
            final int index = i;
            addParamSlider(instrumentSection.content(),
                    engine.getYawnInstrumentParamName(i),
                    engine.getYawnInstrumentParamMin(i),
                    engine.getYawnInstrumentParamMax(i),
                    engine.getYawnInstrumentParamValue(i),
                    value -> engine.setYawnInstrumentParam(index, value));
        }
        soundControls.addView(instrumentSection.root(),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        CollapsibleSection tempoSection = new CollapsibleSection(this,
                "Tempo / output", true);
        addParamSlider(tempoSection.content(), "Tempo", 20.0f, 300.0f,
                yawnTempo, value -> {
                    yawnTempo = value;
                    engine.setYawnTempo(value);
                    prefs.writeInt("yawnTempo", (int) (value * 10));
                });
        addParamSlider(tempoSection.content(), "Master volume", 0.0f, 2.0f,
                yawnMasterVolume, value -> {
                    yawnMasterVolume = value;
                    engine.setYawnMasterVolume(value);
                    prefs.writeInt("yawnMasterVolume", (int) (value * 1000));
                });
        soundControls.addView(tempoSection.root(),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        midiFxSection = new CollapsibleSection(this, "MIDI effects", false);
        fxControls.addView(midiFxSection.root(),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        rebuildEffectChain(true);

        audioFxSection = new CollapsibleSection(this, "Audio effects", false);
        fxControls.addView(audioFxSection.root(),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        rebuildEffectChain(false);
    }

    private void rebuildEffectChain(boolean midi) {
        CollapsibleSection section = midi ? midiFxSection : audioFxSection;
        if (section == null || engine == null) {
            return;
        }
        LinearLayout content = section.content();
        content.removeAllViews();
        int count = midi ? engine.getYawnMidiChainSize()
                : engine.getYawnAudioChainSize();
        section.setTitle((midi ? "MIDI effects" : "Audio effects")
                + " (" + count + ")");
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("No effects in this chain yet.");
            empty.setTextSize(12);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setPadding(0, dp(6), 0, dp(6));
            content.addView(empty);
        }
        for (int slot = 0; slot < count; slot++) {
            content.addView(buildEffectSlotRow(midi, slot, count));
        }
        content.addView(buildAddEffectRow(midi, count));
        if (modMatrix != null && !midi) {
            modMatrix.refreshTargets();
        }
    }

    private LinearLayout buildEffectSlotRow(boolean midi, int slot, int count) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.bg_effect_slot);
        block.setClipToOutline(true);
        block.setPadding(dp(10), dp(8), dp(10), dp(10));
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blockParams.bottomMargin = dp(6);
        block.setLayoutParams(blockParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ToggleButton enabled = new ToggleButton(this);
        enabled.setTextOn("ON");
        enabled.setTextOff("OFF");
        enabled.setTextSize(10);
        enabled.setBackgroundResource(R.drawable.bg_toggle);
        enabled.setTextColor(getColorStateList(R.color.toggle_text));
        enabled.setMinWidth(dp(54));
        enabled.setMinimumWidth(dp(54));
        enabled.setMinHeight(dp(34));
        enabled.setMinimumHeight(dp(34));
        enabled.setPadding(dp(8), 0, dp(8), 0);
        boolean bypassed = midi ? engine.isYawnMidiChainBypassed(slot)
                : engine.isYawnAudioChainBypassed(slot);
        enabled.setChecked(!bypassed);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (midi) {
                engine.setYawnMidiChainBypassed(slot, !checked);
            } else {
                engine.setYawnAudioChainBypassed(slot, !checked);
            }
            scheduleSaveSynthState();
        });
        titleRow.addView(enabled);

        TextView name = new TextView(this);
        name.setTextSize(13);
        name.setTextColor(getColor(R.color.text_primary));
        name.setTypeface(Typeface.create("sans-serif-medium",
                Typeface.NORMAL));
        name.setPadding(dp(10), 0, 0, 0);
        name.setText((slot + 1) + ". " + (midi
                ? engine.getYawnMidiChainName(slot)
                : engine.getYawnAudioChainName(slot)));
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        titleRow.addView(name);
        block.addView(titleRow);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, dp(4), 0, dp(4));
        Button up = smallButton("Up", slot > 0, () -> {
            if (midi) {
                engine.moveYawnMidiEffect(slot, -1);
            } else {
                engine.moveYawnAudioEffect(slot, -1);
            }
            rebuildEffectChain(midi);
            scheduleSaveSynthState();
        });
        Button down = smallButton("Down", slot < count - 1, () -> {
            if (midi) {
                engine.moveYawnMidiEffect(slot, 1);
            } else {
                engine.moveYawnAudioEffect(slot, 1);
            }
            rebuildEffectChain(midi);
            scheduleSaveSynthState();
        });
        Button remove = smallButton("Remove", true, () -> {
            if (midi) {
                engine.removeYawnMidiEffect(slot);
            } else {
                engine.removeYawnAudioEffect(slot);
            }
            rebuildEffectChain(midi);
            scheduleSaveSynthState();
        });
        LinearLayout params = new LinearLayout(this);
        params.setOrientation(LinearLayout.VERTICAL);
        params.setVisibility(View.GONE);
        Button edit = smallButton("Params", true, () ->
                params.setVisibility(params.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE));
        for (Button button : new Button[]{up, down, remove, edit}) {
            LinearLayout.LayoutParams buttonParams =
                    new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            buttonParams.setMargins(dp(4), 0, dp(4), 0);
            button.setLayoutParams(buttonParams);
            controls.addView(button);
        }
        block.addView(controls);

        int paramCount = midi ? engine.getYawnMidiChainParamCount(slot)
                : engine.getYawnAudioChainParamCount(slot);
        if (paramCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("No parameters");
            params.addView(empty);
        }
        for (int i = 0; i < paramCount; i++) {
            final int parameter = i;
            String parameterName = midi
                    ? engine.getYawnMidiChainParamName(slot, i)
                    : engine.getYawnAudioChainParamName(slot, i);
            float min = midi ? engine.getYawnMidiChainParamMin(slot, i)
                    : engine.getYawnAudioChainParamMin(slot, i);
            float max = midi ? engine.getYawnMidiChainParamMax(slot, i)
                    : engine.getYawnAudioChainParamMax(slot, i);
            float value = midi ? engine.getYawnMidiChainParamValue(slot, i)
                    : engine.getYawnAudioChainParamValue(slot, i);
            addParamSlider(params, parameterName, min, max, value, v -> {
                if (midi) {
                    engine.setYawnMidiChainParam(slot, parameter, v);
                } else {
                    engine.setYawnAudioChainParam(slot, parameter, v);
                }
            });
        }
        block.addView(params);
        return block;
    }

    private LinearLayout buildAddEffectRow(boolean midi, int count) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, 0);

        TextView label = new TextView(this);
        label.setTextSize(12);
        label.setTextColor(getColor(R.color.text_secondary));
        label.setPadding(0, dp(8), 0, dp(4));
        label.setText("Add effect");
        block.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int typeCount = midi ? engine.getYawnMidiEffectCount()
                : engine.getYawnAudioEffectCount();
        String[] names = new String[typeCount];
        for (int i = 0; i < typeCount; i++) {
            names[i] = midi ? engine.getYawnMidiEffectName(i)
                    : engine.getYawnAudioEffectName(i);
        }
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(spinnerAdapter(names));
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(spinner);

        Button add = new Button(this);
        add.setText("Add");
        add.setTextSize(10);
        add.setBackgroundResource(R.drawable.bg_button_primary);
        add.setTextColor(getColorStateList(
                R.color.button_primary_text_selector));
        add.setMinHeight(dp(44));
        add.setMinimumHeight(dp(44));
        add.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        addParams.setMargins(dp(8), 0, 0, 0);
        add.setLayoutParams(addParams);
        boolean chainFull = count >= (midi ? YawnEngineLimits.MIDI_CHAIN
                : YawnEngineLimits.AUDIO_CHAIN);
        add.setEnabled(!chainFull);
        add.setAlpha(chainFull ? 0.35f : 1.0f);
        add.setOnClickListener(v -> {
            int type = spinner.getSelectedItemPosition();
            int slot = midi ? engine.addYawnMidiEffect(type)
                    : engine.addYawnAudioEffect(type);
            if (slot < 0) {
                Toast.makeText(this, "Effect chain is full",
                        Toast.LENGTH_SHORT).show();
            } else {
                rebuildEffectChain(midi);
                scheduleSaveSynthState();
            }
        });
        row.addView(add);
        block.addView(row);
        return block;
    }

    private Button smallButton(String text, boolean enabled, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(9);
        button.setTextColor(getColorStateList(
                R.color.button_text_selector));
        button.setBackgroundResource(R.drawable.bg_button_secondary);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        button.setPadding(dp(4), dp(6), dp(4), dp(6));
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.35f);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void installCollapsibleXmlSections() {
        if (optionsScrollView == null) {
            optionsScrollView = findViewById(R.id.optionsScrollView);
        }
        if (optionsScrollView == null) {
            return;
        }
        View child = optionsScrollView.getChildAt(0);
        if (!(child instanceof LinearLayout)) {
            return;
        }
        LinearLayout root = (LinearLayout) child;
        List<TextView> headers = new ArrayList<>();
        List<List<View>> groups = new ArrayList<>();
        List<Boolean> collapsed = new ArrayList<>();
        List<String> tabs = new ArrayList<>();
        TextView currentHeader = null;
        List<View> currentGroup = null;
        boolean currentCollapsed = false;
        String currentTab = "play";

        for (int i = 0; i < root.getChildCount(); i++) {
            View view = root.getChildAt(i);
            if (view instanceof TextView) {
                Object tag = view.getTag();
                if (tag != null && tag.toString().startsWith("section")) {
                    if (currentHeader != null) {
                        headers.add(currentHeader);
                        groups.add(currentGroup);
                        collapsed.add(currentCollapsed);
                        tabs.add(currentTab);
                    }
                    String rawTag = tag.toString();
                    currentHeader = (TextView) view;
                    currentGroup = new ArrayList<>();
                    currentCollapsed = rawTag.startsWith("section_collapsed");
                    currentTab = "play";
                    int colon = rawTag.indexOf(':');
                    if (colon >= 0 && colon + 1 < rawTag.length()) {
                        currentTab = rawTag.substring(colon + 1);
                    }
                    continue;
                }
            }
            if (currentGroup != null) {
                currentGroup.add(view);
            }
        }
        if (currentHeader != null) {
            headers.add(currentHeader);
            groups.add(currentGroup);
            collapsed.add(currentCollapsed);
            tabs.add(currentTab);
        }
        if (headers.isEmpty()) {
            return;
        }

        root.removeAllViews();
        settingsCards.clear();
        settingsCardTabs.clear();

        for (int i = 0; i < headers.size(); i++) {
            final TextView header = headers.get(i);
            final List<View> group = groups.get(i);
            final String tab = tabs.get(i);
            String title = header.getText().toString();
            if (title.startsWith("▼ ") || title.startsWith("▶ ")
                    || title.startsWith("▾ ") || title.startsWith("▸ ")) {
                title = title.substring(2);
            }
            final String baseTitle = title;
            final boolean[] expanded = {!collapsed.get(i)};

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setClipToOutline(true);
            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(8);
            card.setLayoutParams(cardParams);

            styleSectionHeader(header);
            header.setText((expanded[0] ? "▾ " : "▸ ") + baseTitle);
            header.setTag(null);
            header.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            final LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(14), dp(4), dp(14), dp(14));
            for (int j = 0; j < group.size(); j++) {
                if (j > 0) {
                    View spacer = new View(this);
                    spacer.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));
                    content.addView(spacer);
                }
                content.addView(group.get(j));
            }

            final View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.divider));
            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            dividerParams.setMargins(dp(14), 0, dp(14), 0);
            divider.setLayoutParams(dividerParams);

            card.addView(header);
            card.addView(divider);
            card.addView(content);
            root.addView(card);

            content.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            divider.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            header.setOnClickListener(v -> {
                expanded[0] = !expanded[0];
                content.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
                divider.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
                header.setText((expanded[0] ? "▾ " : "▸ ") + baseTitle);
            });

            settingsCards.add(card);
            settingsCardTabs.add(tab);
        }
    }

    private void styleSectionHeader(TextView header) {
        header.setTextSize(13);
        header.setTextColor(getColor(R.color.accent));
        header.setTypeface(Typeface.create("sans-serif-medium",
                Typeface.NORMAL));
        header.setLetterSpacing(0.06f);
        header.setIncludeFontPadding(false);
        header.setPadding(dp(14), dp(12), dp(14), dp(10));
    }

    private void setupSettingsTabs() {
        settingsTabButtons.clear();
        settingsTabKeys.clear();
        int[] ids = {R.id.tabPlay, R.id.tabSound, R.id.tabFx,
                R.id.tabTune, R.id.tabIo, R.id.tabMod};
        String[] keys = {"play", "sound", "fx", "tune", "io", "mod"};
        for (int i = 0; i < ids.length; i++) {
            TextView button = findViewById(ids[i]);
            if (button == null) {
                continue;
            }
            final String key = keys[i];
            button.setOnClickListener(v -> selectSettingsTab(key));
            settingsTabButtons.add(button);
            settingsTabKeys.add(key);
        }
        currentSettingsTab = prefs.readString("settingsTab", "play");
        selectSettingsTab(currentSettingsTab);
    }

    private void selectSettingsTab(String tab) {
        if (tab == null) {
            tab = "play";
        }
        boolean found = false;
        for (int i = 0; i < settingsTabKeys.size(); i++) {
            boolean selected = settingsTabKeys.get(i).equals(tab);
            settingsTabButtons.get(i).setSelected(selected);
            found |= selected;
        }
        if (!found) {
            tab = "play";
        }
        for (int i = 0; i < settingsCards.size(); i++) {
            settingsCards.get(i).setVisibility(
                    settingsCardTabs.get(i).equals(tab)
                            ? View.VISIBLE : View.GONE);
        }
        currentSettingsTab = tab;
        if (prefs != null) {
            prefs.writeString("settingsTab", tab);
        }
        if (optionsScrollView != null) {
            optionsScrollView.scrollTo(0, 0);
        }
    }

    private void showSettings() {
        if (settingsPanel == null) {
            return;
        }
        settingsPanel.setVisibility(View.VISIBLE);
        if (menuView != null) {
            menuView.setVisibility(View.GONE);
        }
        selectSettingsTab(currentSettingsTab);
    }

    private void hideSettings() {
        if (settingsPanel == null) {
            return;
        }
        settingsPanel.setVisibility(View.GONE);
        if (menuView != null) {
            menuView.setVisibility(View.VISIBLE);
        }
    }

    private void setupScopeModeControls() {
        scopeModeWave = findViewById(R.id.scopeModeWave);
        scopeModeFft = findViewById(R.id.scopeModeFft);
        scopeModeBoth = findViewById(R.id.scopeModeBoth);
        if (scopeModeWave == null || scopeModeFft == null
                || scopeModeBoth == null) {
            return;
        }
        scopeMode = prefs.readInt("scopeMode", Scope.MODE_BOTH);
        scopeModeWave.setOnClickListener(v -> setScopeMode(Scope.MODE_WAVE));
        scopeModeFft.setOnClickListener(v -> setScopeMode(Scope.MODE_FFT));
        scopeModeBoth.setOnClickListener(v -> setScopeMode(Scope.MODE_BOTH));
        applyScopeMode();
    }

    private void setScopeMode(int mode) {
        scopeMode = mode;
        prefs.writeInt("scopeMode", mode);
        applyScopeMode();
    }

    private void applyScopeMode() {
        if (scopeModeWave == null) {
            return;
        }
        scopeModeWave.setSelected(scopeMode == Scope.MODE_WAVE);
        scopeModeFft.setSelected(scopeMode == Scope.MODE_FFT);
        scopeModeBoth.setSelected(scopeMode == Scope.MODE_BOTH);
        if (scope != null) {
            scope.setMode(scopeMode);
        }
    }

    private static final class YawnEngineLimits {
        static final int MIDI_CHAIN = 8;
        static final int AUDIO_CHAIN = 8;
    }

    private ArrayAdapter<String> spinnerAdapter(String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void addInfoText(LinearLayout parent, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setPadding(0, dp(6), 0, dp(6));
        parent.addView(view);
    }

    private void addParamSlider(LinearLayout parent, String name, float min,
                                float max, float value, ParamSetter setter) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(6), 0, dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(12);
        label.setTextColor(getColor(R.color.text_secondary));
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(label);

        TextView valueView = new TextView(this);
        valueView.setTextSize(12);
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setTextColor(getColor(R.color.accent));
        valueView.setText(formatParam(value));
        header.addView(valueView);

        SeekBar bar = new SeekBar(this);
        bar.setMax(1000);
        bar.setContentDescription(name);
        final float range = max - min;
        int progress = range > 0.0f
                ? Math.round((value - min) / range * 1000.0f) : 0;
        progress = Math.max(0, Math.min(1000, progress));
        bar.setProgress(progress);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue,
                                          boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                float newValue = min + range * progressValue / 1000.0f;
                valueView.setText(formatParam(newValue));
                setter.set(newValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleSaveSynthState();
            }
        });
        block.addView(header);
        block.addView(bar);
        parent.addView(block);
    }

    private static String formatParam(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private interface ParamSetter {
        void set(float value);
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("The wavetable file is too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String displayNameFromUri(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path == null || path.isEmpty()) {
            return "Imported wavetable";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    @Override
    protected void onPause() {
        saveSynthStateNow();
        if (modMatrix != null) {
            modMatrix.onPause();
        }
        resumed = false;
        if (mHandler != null) {
            mHandler.removeCallbacks(audioResumeRetry);
        }
        stopAllTouches(true);
        super.onPause();
        if (mHandler != null && screenUpdater != null) {
            mHandler.removeCallbacks(screenUpdater);
        }
        if (engine != null) {
            engine.pauseAudio();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (engine != null) {
            engine.resumeAudio();
        }
        if (mHandler != null) {
            mHandler.removeCallbacks(audioResumeRetry);
            mHandler.postDelayed(audioResumeRetry, 500);
            mHandler.postDelayed(audioResumeRetry, 1500);
        }
        if (mHandler != null && screenUpdater != null) {
            mHandler.removeCallbacks(screenUpdater);
            mHandler.postDelayed(screenUpdater, updateInterval);
        }
        if (modMatrix != null) {
            modMatrix.onResume();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && resumed && engine != null) {
            engine.resumeAudio();
        }
    }

    @Override
    protected void onDestroy() {
        saveSynthStateNow();
        resumed = false;
        if (mHandler != null) {
            mHandler.removeCallbacks(audioResumeRetry);
        }
        if (mHandler != null && screenUpdater != null) {
            mHandler.removeCallbacks(screenUpdater);
        }
        if (rtpMidiClient != null) {
            rtpMidiClient.stop();
            rtpMidiClient = null;
        }
        stopAllTouches(true);
        if (engine != null) {
            engine.shutdownVAEngine();
            engine = null;
        }
        super.onDestroy();
    }

}
