package org.bokontep.wavesynth;

import androidx.appcompat.app.AppCompatActivity;


import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
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

    private int[] scales =
            {
                    12, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, // chromatic
                    7, 0, 2, 4, 5, 7, 9, 11, 0, 0, 0, 0, 0, // major
                    7, 0, 2, 3, 5, 7, 8, 10, 0, 0, 0, 0, 0, // natural minor
                    7, 0, 2, 3, 5, 7, 8, 11, 0, 0, 0, 0, 0, // harmonic minor
                    7, 0, 2, 3, 5, 7, 9, 11, 0, 0, 0, 0, 0, // ascending melodic minor
                    8, 0, 2, 4, 6, 7, 9, 10, 11, 0, 0, 0, 0, // acoustic
                    10, 0, 1, 2, 4, 5, 7, 8, 9, 10, 11, 0, 0, // major2
                    8, 0, 1, 4, 5, 7, 8, 10, 11, 0, 0, 0, 0, // minor2
                    5, 0, 3, 5, 7, 10, 0, 0, 0, 0, 0, 0, 0, // pentatonic
                    6, 0, 3, 5, 6, 7, 10, 0, 0, 0, 0, 0, 0, // blues
                    5, 0, 2, 5, 7, 9, 0, 0, 0, 0, 0, 0, 0, // chinese pentatonic
                    6, 0, 2, 4, 6, 8, 10, 0, 0, 0, 0, 0, 0, // whole tone
                    8, 0, 2, 3, 5, 6, 8, 9, 11, 0, 0, 0, 0, // whole half
                    8, 0, 1, 3, 4, 6, 7, 9, 10, 0, 0, 0, 0  // half whole


            };
    private long lastTouchEventTime = 0;


    private String midiLog = "";
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

    private int lowOffset = 0;
    private int midOffset = 12;
    private int highOffset = 24;
    private float tune = 440.0f;
    private float octaveFactor = 2.0f;
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
    private EditText lowOffsetEditText;
    private EditText midOffsetEditText;
    private EditText highOffsetEditText;

    private WavetableStore wavetableStore;
    private TextView customWavetableText;
    private Spinner engineSpinner;
    private LinearLayout yawnControls;
    private CollapsibleSection midiFxSection;
    private CollapsibleSection audioFxSection;
    private float yawnTempo = 120.0f;
    private float yawnMasterVolume = 0.8f;

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
    private Spinner scaleSpinner;
    private boolean legato = true;
    private Spinner rootNoteSpinner;
    private SeekBar tetSeekBar;
    private SeekBar tuneSeekBar;
    private SeekBar octaveFactorSeekBar;
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
    private Button clearButton;
    private Button tempoButton;
    private View optionsScrollView;
    private View menuView;
    private int rootNote = 36;
    private int xNoteScale = 160;
    private int currentScale = 0;
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
    private String[] scaleNames =
            {
                    "chromatic",
                    "major",
                    "natural minor",
                    "harmonic minor",
                    "ascending melodic minor",
                    "acoustic",
                    "major2",
                    "minor2",
                    "pentatonic",
                    "blues",
                    "chinese pentatonic",
                    "whole tone",
                    "whole half",
                    "half whole"
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
        lowOffset = prefs.readInt("lowOffset",0);
        midOffset = prefs.readInt("midOffset",12);
        highOffset = prefs.readInt("highOffset",24);
        delayLevel = prefs.readInt("delayLevel",0);
        delayTime = prefs.readInt("delayTime",0);
        delayFeedback = prefs.readInt("delayFeedback",0);
        engine.setEngineMode(prefs.readInt("engineMode",
                SynthEngine.ENGINE_YAWN_WAVETABLE));
        yawnTempo = prefs.readInt("yawnTempo", 1200) / 10.0f;
        yawnMasterVolume = prefs.readInt("yawnMasterVolume", 800) / 1000.0f;
        engine.initAudio();
        if (engine.getEngineMode() != SynthEngine.ENGINE_LEGACY) {
            engine.setYawnTempo(yawnTempo);
            engine.setYawnMasterVolume(yawnMasterVolume);
        }

        tune = (prefs.readInt("tune", 4400) / 10.0f);
        tet = prefs.readInt("tet", 12);
        octaveFactor = (prefs.readInt("octaveFactor",2000)/1000.0f);
        red = prefs.readInt("red", 0) == 0 ? false : true;
        legato = prefs.readInt("legato", 0)==0?false:true;

        rootNote = prefs.readInt("rootNote", 35);
        xNoteScale = prefs.readInt("xNoteScale", 160);
        currentScale = prefs.readInt("currentScale", 0);
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
                "YAWN Subtractive"
        };
        engineSpinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, engineNames));
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
                prefs.writeInt("engineMode", position);
                if (position != SynthEngine.ENGINE_LEGACY) {
                    restoreSynthState();
                }
                rebuildYawnControls();
                updateWavetableViews();
                if (wavetableStore.hasStoredWavetable()) {
                    restoreSavedWavetable();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        yawnControls = (LinearLayout) findViewById(R.id.yawnControls);
        rebuildYawnControls();
        installCollapsibleXmlSections();
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

        scope = findViewById(R.id.mscope);
        scope.setXNoteScale(xNoteScale);
        scope.setRed(red);
        updateScopeText();
        rootNoteSpinner = (Spinner) findViewById(R.id.rootNoteSpinner);
        final ArrayAdapter<String> rootNoteAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, rootNotes);

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
        final ArrayAdapter<String> scaleAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, scaleNames);
        scaleSpinner = (Spinner) findViewById(R.id.scaleSpinner);
        scaleSpinner.setAdapter(scaleAdapter);

        scaleSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position >= 0 && position < scaleAdapter.getCount()) {
                            currentScale = position;
                            prefs.writeInt("currentScale", currentScale);
                            updatePitchMapper();
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        return;
                    }
                });
        scaleSpinner.setSelection(currentScale);
        this.tetSeekBar = (SeekBar) findViewById(R.id.tetSeekBar);
        this.tetSeekBar.setProgress(tet);
        this.tetTextView = (TextView) findViewById(R.id.tetText);
        this.tetTextView.setText("Tuning Equal Temperament (TET):" + tet);
        this.tuneSeekBar = (SeekBar) findViewById(R.id.tuneSeekBar);
        this.tuneSeekBar.setProgress((int) (tune * 10));
        this.octaveFactorTextView = (TextView)findViewById(R.id.octaveFactorText);
        this.octaveFactorTextView.setText("Octave factor (default is 2):"+octaveFactor);
        this.octaveFactorSeekBar = (SeekBar)findViewById(R.id.octaveFactorSeekBar);
        this.octaveFactorSeekBar.setProgress((int) (octaveFactor * 1000));


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
                dspLoadText.setText("DSP: --");
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
        this.tuneTextView.setText("A frequency (Hz):" + tune);
        this.osc1AttackTextView = (TextView) findViewById(R.id.osc1AttackText);
        this.osc1AttackTextView.setText("osc1Attack:" + engine.getOsc1Attack());
        this.osc1DecayTextView = (TextView) findViewById(R.id.osc1DecayText);
        this.osc1DecayTextView.setText("osc1Decay:" + engine.getOsc1Decay());
        this.osc1SustainTextView = (TextView) findViewById(R.id.osc1SustainText);
        this.osc1SustainTextView.setText("osc1Sustain:" + engine.getOsc1Sustain());
        this.osc1ReleaseTextView = (TextView) findViewById(R.id.osc1ReleaseText);
        this.osc1ReleaseTextView.setText("osc1Release:" + engine.getOsc1Release());
        this.osc2AttackTextView = (TextView) findViewById(R.id.osc2AttackText);
        this.osc2AttackTextView.setText("osc2Attack:" + engine.getOsc2Attack());
        this.osc2DecayTextView = (TextView) findViewById(R.id.osc2DecayText);
        this.osc2DecayTextView.setText("osc2Decay:" + engine.getOsc2Decay());
        this.osc2SustainTextView = (TextView) findViewById(R.id.osc2SustainText);
        this.osc2SustainTextView.setText("osc2Sustain:" + engine.getOsc2Sustain());
        this.osc2ReleaseTextView = (TextView) findViewById(R.id.osc2ReleaseText);
        this.osc2ReleaseTextView.setText("osc2Release:" + engine.getOsc2Release());

        this.oscSpreadTextView = (TextView) findViewById(R.id.oscSpreadText);
        this.oscSpreadTextView.setText("oscSpread:" + maxSpread);
        this.osc1WaveTextView = (TextView) findViewById(R.id.osc1WaveText);
        this.osc1WaveTextView.setText("osc1Wave:" + osc1Wave);
        this.osc2WaveTextView = (TextView) findViewById(R.id.osc2WaveText);
        this.osc2WaveTextView.setText("osc2Wave:" + osc2Wave);
        this.osc1WaveControlTextView = (TextView) findViewById(R.id.osc1WaveControlText);
        this.osc1WaveControlTextView.setText("osc1WaveControl:" + osc1WaveControl);
        this.osc2WaveControlTextView = (TextView) findViewById(R.id.osc2WaveControlText);
        this.osc2WaveControlTextView.setText("osc2WaveControl:" + osc2WaveControl);
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
        this.lowOffsetEditText = (EditText) findViewById(R.id.lowOffsetEditText);
        this.midOffsetEditText = (EditText) findViewById(R.id.middleOffsetEditText);
        this.highOffsetEditText = (EditText) findViewById(R.id.highOffsetEditText);
        this.lowOffsetEditText.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        try {
                            lowOffset = Integer.parseInt(lowOffsetEditText.getText().toString(),10);
                            if(lowOffset<0 || lowOffset>2*tet)
                            {
                                lowOffset = 0;
                            }
                            prefs.writeInt("lowOffset",lowOffset);
                            updatePitchMapper();
                        }
                        catch (Exception e)
                        {
                            lowOffset = 0;
                        }
                    }
                }
        );
        this.midOffsetEditText.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        try {
                            midOffset = Integer.parseInt(midOffsetEditText.getText().toString(),10);
                            if(midOffset<0 || midOffset>2*tet)
                            {
                                midOffset = 0;
                            }
                            prefs.writeInt("midOffset",midOffset);
                            updatePitchMapper();
                        }
                        catch (Exception e)
                        {
                            midOffset = 0;
                        }
                    }
                }
        );
        this.highOffsetEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    highOffset = Integer.parseInt(highOffsetEditText.getText().toString(),10);
                    if(highOffset<0 || highOffset>2*tet)
                    {
                        highOffset = 0;
                    }
                    prefs.writeInt("highOffset",highOffset);
                    updatePitchMapper();
                }
                catch (Exception e)
                {
                    highOffset = 0;
                }
            }
        });
        //this.osc2WaveDisplay.invalidate();
        SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser)
                    return;
                int id = seekBar.getId();
                if (id == R.id.tuneSeekBar) {
                    tune = ((float) progress) / 10.0f;
                    tuneTextView.setText("A frequency (Hz):" + tune);
                    engine.setTune(tune);
                    prefs.writeInt("tune", (int) (tune * 10));
                    updatePitchMapper();
                } else if (id == R.id.tetSeekBar) {
                    tet = progress;
                    engine.setTet(tet);
                    tetTextView.setText("Tuning Equal Temperament:" + tet);
                    prefs.writeInt("tet", tet);
                    updatePitchMapper();
                } else if (id == R.id.octaveFactorSeekBar) {
                    octaveFactor = ((float)progress) / 1000.0f;
                    octaveFactorTextView.setText("Octave factor (default is 2):"+octaveFactor);
                    engine.setOctaveFactor(octaveFactor);
                    prefs.writeInt("octaveFactor",(int)(octaveFactor*1000));
                    updatePitchMapper();
                } else if (id == R.id.osc1AttackSeekBar) {
                    engine.setOsc1Attack(progress);
                    osc1AttackTextView.setText("osc1Attack:" + engine.getOsc1Attack());
                    prefs.writeInt("osc1Attack", engine.getOsc1Attack());
                    engine.sendMidiCC(0, 18, engine.getOsc1Attack());
                } else if (id == R.id.osc1DecaySeekBar) {
                    engine.setOsc1Decay(progress);
                    osc1DecayTextView.setText("osc1Decay:" + engine.getOsc1Decay());
                    prefs.writeInt("osc1Decay", engine.getOsc1Decay());
                    engine.sendMidiCC(0, 19, engine.getOsc1Decay());
                } else if (id == R.id.osc1SustainSeekBar) {
                    engine.setOsc1Sustain(progress);
                    osc1SustainTextView.setText("osc1Sustain:" + engine.getOsc1Sustain());
                    prefs.writeInt("osc1Sustain", engine.getOsc1Sustain());
                    engine.sendMidiCC(0, 20, engine.getOsc1Sustain());
                } else if (id == R.id.osc1ReleaseSeekBar) {
                    engine.setOsc1Release(progress);
                    osc1ReleaseTextView.setText("osc1Release:" + engine.getOsc1Release());
                    prefs.writeInt("osc1Release", engine.getOsc1Release());
                    engine.sendMidiCC(0, 21, engine.getOsc1Release());
                } else if (id == R.id.osc2AttackSeekBar) {
                    engine.setOsc2Attack(progress);
                    osc2AttackTextView.setText("osc2Attack:" + engine.getOsc2Attack());
                    prefs.writeInt("osc2Attack", engine.getOsc2Attack());
                    engine.sendMidiCC(0, 22, engine.getOsc2Attack());
                } else if (id == R.id.osc2DecaySeekBar) {
                    engine.setOsc2Decay(progress);
                    osc2DecayTextView.setText("osc2Decay:" + engine.getOsc2Decay());
                    prefs.writeInt("osc2Decay", engine.getOsc2Decay());
                    engine.sendMidiCC(0, 23, engine.getOsc2Decay());
                } else if (id == R.id.osc2SustainSeekBar) {
                    engine.setOsc2Sustain(progress);
                    osc2SustainTextView.setText("osc2Sustain:" + engine.getOsc2Sustain());
                    prefs.writeInt("osc2Sustain", engine.getOsc2Sustain());
                    engine.sendMidiCC(0, 24, engine.getOsc2Sustain());
                } else if (id == R.id.osc2ReleaseSeekBar) {
                    engine.setOsc2Release(progress);
                    osc2ReleaseTextView.setText("osc2Release:" + engine.getOsc2Release());
                    prefs.writeInt("osc2Release", engine.getOsc2Release());
                    engine.sendMidiCC(0, 25, engine.getOsc2Release());
                } else if (id == R.id.maxSpreadSeekBar) {
                    maxSpread = progress;
                    oscSpreadTextView.setText("oscSpread:" + maxSpread);
                    prefs.writeInt("maxSpread", maxSpread);
                } else if (id == R.id.osc1WaveSeekBar) {
                    osc1Wave = progress;
                    osc1WaveDisplay.setData(engine.getWavetable(osc1Wave));
                    engine.sendMidiCC(0,16,osc1Wave);
                    osc1WaveTextView.setText("osc1Wave:" + osc1Wave);
                    osc1WaveDisplay.invalidate();
                    prefs.writeInt("osc1Wave", osc1Wave);
                } else if (id == R.id.osc2WaveSeekBar) {
                    osc2Wave = progress;
                    osc2WaveDisplay.setData(engine.getWavetable(osc2Wave));
                    engine.sendMidiCC(0,17,osc2Wave);
                    osc2WaveTextView.setText("osc2Wave:" + osc2Wave);
                    osc2WaveDisplay.invalidate();
                    prefs.writeInt("osc2Wave", osc2Wave);
                } else if (id == R.id.osc1WaveControlSeekBar) {
                    osc1WaveControl = progress;
                    osc1WaveControlTextView.setText("osc1WaveControl:" + osc1WaveControl);
                    prefs.writeInt("osc1WaveControl", osc1WaveControl);
                } else if (id == R.id.osc2WaveControlSeekBar) {
                    osc2WaveControl = progress;
                    osc2WaveControlTextView.setText("osc2WaveControl:" + osc2WaveControl);
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
        this.recButton.setBackgroundColor(Color.GRAY);
        this.playButton = (Button)findViewById(R.id.playButton);
        this.clearButton = (Button)findViewById(R.id.clearButton);
        this.tempoButton = (Button)findViewById(R.id.tempoButton);
        this.recButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!record)
                {
                    record = true;
                    engine.setRecord(record);
                    recButton.setBackgroundColor(Color.RED);
                }
                else
                {
                    record = false;
                    engine.setRecord(record);
                    recButton.setBackgroundColor(Color.GRAY);
                }
                recButton.invalidate();
            }
        });
        this.playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!play)
                {
                    play = true;
                    engine.setPlay(play);
                    playButton.setBackgroundColor(Color.GREEN);

                }
                else
                {
                    play = false;
                    engine.setPlay(play);
                    playButton.setBackgroundColor(Color.GRAY);
                }
            }
        });
        //this.recButton.setVisibility(View.INVISIBLE);
        //this.playButton.setVisibility(View.INVISIBLE);
        this.clearButton.setVisibility(View.INVISIBLE);
        this.tempoButton.setVisibility(View.INVISIBLE);
        this.optionsScrollView = findViewById(R.id.optionsScrollView);
        this.menuView = findViewById(R.id.menu);
        this.optionsScrollView.setVisibility(View.GONE);
        this.menuView.setVisibility(View.VISIBLE);
        this.settingsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (optionsScrollView.getVisibility() == View.GONE) {
                            menuView.setVisibility(View.GONE);
                            optionsScrollView.setVisibility(View.VISIBLE);
                        }
                    }
                }
        );
        findViewById(R.id.closeSettingsButton).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //applySettings();
                        menuView.setVisibility(View.VISIBLE);
                        optionsScrollView.setVisibility(View.GONE);
                    }
                }

        );
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
                scope.setData(engine.getWaveform());
                scope.setMidilog(getNoteMap());
                updateDspMeter();
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

    String getNoteMap() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < touchVoices.size(); i++) {
            s.append(touchVoices.keyAt(i)).append(':')
                    .append(touchVoices.valueAt(i).anchorNote).append(' ');
        }
        return s.toString();
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

    private void updateDspMeter() {
        if (dspLoadText == null || engine == null) {
            return;
        }
        if ((dspUiCounter++ % 5) != 0) {
            return;
        }
        float load = engine.getDspLoad() * 100.0f;
        float peak = engine.getDspPeak() * 100.0f;
        dspLoadText.setText(String.format(Locale.US,
                "DSP: %.1f%% (peak %.1f%%)", load, peak));
        if (load > 80.0f) {
            dspLoadText.setTextColor(Color.RED);
        } else if (load > 60.0f) {
            dspLoadText.setTextColor(Color.rgb(255, 165, 0));
        } else {
            dspLoadText.setTextColor(Color.GREEN);
        }
    }

    private void updateGridSizeLabel() {
        if (gridSizeTextView != null) {
            gridSizeTextView.setText("Grid size: " + xNoteScale + " px per note");
        }
    }

    private void updatePitchMapper() {
        int[] scaleRow = new int[13];
        System.arraycopy(scales, currentScale * 13, scaleRow, 0, 13);
        pitchMapper = new MicrotonalPitchMapper(rootNote, scaleRow, tet, tune,
                octaveFactor, xNoteScale);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
        float height = getWindow().getDecorView().getHeight();
        if (height <= 0) {
            height = getResources().getDisplayMetrics().heightPixels;
        }
        return height;
    }

    private int zoneOffset(float y, float height) {
        if (y > 2.0f * height / 3.0f) {
            return lowOffset;
        }
        if (y < height / 3.0f) {
            return highOffset;
        }
        return midOffset;
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
        final MicrotonalPitchMapper.Pitch pitch =
                pitchMapper.pitchAt(x, zoneOffset(y, height));

        TouchVoice voice = new TouchVoice(id, channel);
        voice.velocity = velocity;
        voice.pressure = expression >= 0.0f ? expression : 0.5f;
        int[] waveforms = waveformsForTouch(y, height);
        voice.waveform1 = waveforms[0];
        voice.waveform2 = waveforms[1];
        MicrotonalVoice.Transition transition = voice.expression.start(pitch);
        voice.anchorNote = transition.anchorNote;
        voice.lastSpread = (int) spreadForTouch(x);
        touchVoices.put(id, voice);

        engine.sendMidiNoteOn(0, transition.noteOn, velocity);
        midiNoteOn(channel, transition.noteOn, velocity);
        engine.sendMidiNotePitch(0, transition.noteOn,
                (float) transition.bendSemitones);
        midiPitchBend(channel, pitchBend14(transition.bendSemitones));
        sendTouchPressure(voice);
        selectWaveformForVoice(voice);
        scope.setMarker("" + id, x, y);
        vel = velocity;
    }

    private void updateTouch(TouchVoice voice, MotionEvent event, int index,
                             float height) {
        final float x = event.getX(index);
        final float y = event.getY(index);
        final float expression = rawTouchExpression(event, index);
        final int velocity = velocityForExpression(expression);
        final MicrotonalPitchMapper.Pitch pitch =
                pitchMapper.pitchAt(x, zoneOffset(y, height));
        final MicrotonalVoice.Transition transition =
                voice.expression.update(pitch, legato);

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
        engine.sendMidiNotePitch(0, transition.anchorNote,
                (float) transition.bendSemitones);
        midiPitchBend(voice.channel,
                pitchBend14(transition.bendSemitones));
        sendTouchPressure(voice);

        int[] waveforms = waveformsForTouch(y, height);
        voice.waveform1 = waveforms[0];
        voice.waveform2 = waveforms[1];
        selectWaveformForVoice(voice);

        voice.lastSpread = (int) spreadForTouch(x);
        engine.sendMidiNoteSpread(0, voice.anchorNote,
                63 + voice.lastSpread);
        scope.setMarker("" + voice.pointerId, x, y);
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
        if (unsetMarker) {
            scope.unsetMarker("" + id);
        }
    }

    private void stopAllTouches(boolean unsetMarkers) {
        while (touchVoices.size() > 0) {
            int id = touchVoices.keyAt(0);
            stopTouch(id, unsetMarkers);
        }
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
                    .append(scaleNames[currentScale]).append(" notes:");
        } else {
            text.append("tet ").append(tet).append(" notes:");
        }
        for (int i = 0; i < touchVoices.size(); i++) {
            TouchVoice voice = touchVoices.valueAt(i);
            text.append('[').append(voice.lastSpread).append("] ");
            double target = voice.expression.currentPitch();
            int displayNote = Math.max(0, Math.min(127,
                    (int) Math.round(target)));
            text.append(noteName(displayNote));
            double cents = (target - displayNote) * 100.0;
            if (Math.abs(cents) >= 1.0) {
                text.append(String.format(Locale.US, "%+.0fc", cents));
            }
            text.append('(').append(voice.velocity).append(") ");
        }
        if (legato) {
            text.append(" L");
        }
        scope.setText(text.toString());
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

        TouchVoice(int pointerId, int channel) {
            this.pointerId = pointerId;
            this.channel = channel;
        }
    }

    public void logMidi(byte[] data) {
        if (data != null) {
            if (data.length > 0) {
                this.midiLog = "RX:" + data[0];
                scope.setMidilog(this.midiLog);
            }
        }
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
        if (synthInfo != null) {
            scope.setMidilog("MIDI device found!");
        }
        int portIndex = 0;
        scope.setMidilog("");
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
        if (yawnControls == null || engine == null) {
            return;
        }
        yawnControls.removeAllViews();
        final int mode = engine.getEngineMode();
        if (mode == SynthEngine.ENGINE_LEGACY) {
            yawnControls.setVisibility(View.GONE);
            midiFxSection = null;
            audioFxSection = null;
            return;
        }
        yawnControls.setVisibility(View.VISIBLE);

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
        yawnControls.addView(instrumentSection.root(),
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
        yawnControls.addView(tempoSection.root(),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        midiFxSection = new CollapsibleSection(this, "MIDI effects", false);
        yawnControls.addView(midiFxSection.root(),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        rebuildEffectChain(true);

        audioFxSection = new CollapsibleSection(this, "Audio effects", false);
        yawnControls.addView(audioFxSection.root(),
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
            content.addView(empty);
        }
        for (int slot = 0; slot < count; slot++) {
            content.addView(buildEffectSlotRow(midi, slot, count));
            View divider = new View(this);
            divider.setBackgroundColor(0xFF444444);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            content.addView(divider);
        }
        content.addView(buildAddEffectRow(midi, count));
    }

    private LinearLayout buildEffectSlotRow(boolean midi, int slot, int count) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(8), 0, dp(8));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ToggleButton enabled = new ToggleButton(this);
        enabled.setTextOn("ON");
        enabled.setTextOff("OFF");
        enabled.setTextSize(14);
        enabled.setMinWidth(dp(64));
        enabled.setMinimumWidth(dp(64));
        enabled.setMinHeight(dp(48));
        enabled.setMinimumHeight(dp(48));
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
        name.setTextSize(18);
        name.setPadding(12, 0, 12, 0);
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
        label.setTextSize(16);
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
        spinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(spinner);

        Button add = new Button(this);
        add.setText("Add");
        add.setTextSize(14);
        add.setMinHeight(dp(48));
        add.setMinimumHeight(dp(48));
        add.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        addParams.setMargins(dp(8), 0, 0, 0);
        add.setLayoutParams(addParams);
        add.setEnabled(count < (midi ? YawnEngineLimits.MIDI_CHAIN
                : YawnEngineLimits.AUDIO_CHAIN));
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
        button.setTextSize(14);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setEnabled(enabled);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void installCollapsibleXmlSections() {
        View scroll = findViewById(R.id.optionsScrollView);
        if (!(scroll instanceof ScrollView)) {
            return;
        }
        View child = ((ScrollView) scroll).getChildAt(0);
        if (!(child instanceof LinearLayout)) {
            return;
        }
        LinearLayout root = (LinearLayout) child;
        List<TextView> headers = new ArrayList<>();
        List<List<View>> groups = new ArrayList<>();
        List<Boolean> collapsed = new ArrayList<>();
        TextView currentHeader = null;
        List<View> currentGroup = null;
        boolean currentCollapsed = false;

        for (int i = 0; i < root.getChildCount(); i++) {
            View view = root.getChildAt(i);
            if (view instanceof TextView) {
                Object tag = view.getTag();
                if ("section".equals(tag) || "section_collapsed".equals(tag)) {
                    if (currentHeader != null) {
                        headers.add(currentHeader);
                        groups.add(currentGroup);
                        collapsed.add(currentCollapsed);
                    }
                    currentHeader = (TextView) view;
                    currentGroup = new ArrayList<>();
                    currentCollapsed = "section_collapsed".equals(tag);
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
        }

        for (int i = 0; i < headers.size(); i++) {
            TextView header = headers.get(i);
            List<View> group = groups.get(i);
            String title = header.getText().toString();
            if (title.startsWith("▼ ") || title.startsWith("▶ ")) {
                title = title.substring(2);
            }
            final String baseTitle = title;
            final List<View> content = group;
            final boolean[] expanded = {!collapsed.get(i)};
            header.setText((expanded[0] ? "▼ " : "▶ ") + baseTitle);
            for (View view : content) {
                view.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            }
            header.setOnClickListener(v -> {
                expanded[0] = !expanded[0];
                for (View view : content) {
                    view.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
                }
                header.setText((expanded[0] ? "▼ " : "▶ ") + baseTitle);
            });
        }
    }

    private static final class YawnEngineLimits {
        static final int MIDI_CHAIN = 8;
        static final int AUDIO_CHAIN = 8;
    }

    private void addParamSlider(LinearLayout parent, String name, float min,
                                float max, float value, ParamSetter setter) {
        TextView label = new TextView(this);
        label.setTextSize(16);
        parent.addView(label);
        SeekBar bar = new SeekBar(this);
        bar.setMax(1000);
        final float range = max - min;
        int progress = range > 0.0f
                ? Math.round((value - min) / range * 1000.0f) : 0;
        progress = Math.max(0, Math.min(1000, progress));
        bar.setProgress(progress);
        label.setText(String.format(Locale.US, "%s: %.2f", name, value));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue,
                                          boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                float newValue = min + range * progressValue / 1000.0f;
                label.setText(String.format(Locale.US, "%s: %.2f", name,
                        newValue));
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
        parent.addView(bar);
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
