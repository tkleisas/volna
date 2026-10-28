# volna — wavetable touch synthesizer for Android

**волна** (pronounced *volna*) means “wave” in Russian. Volna is a Bokontep
music project: a landscape, multitouch wavetable instrument for Android. It combines a large expressive touch
surface with four selectable synthesis engines, editable MIDI and audio effect
chains, microtonal tuning, MPE output, and a Wi-Fi RTP-MIDI bridge to
[YAWN](https://github.com/tkleisas/yawn).

Prebuilt APKs are published on the
[releases page](https://github.com/tkleisas/volna/releases).

![Volna feature graphic](./store/feature-graphic-1024x500.png?raw=true)

![Volna multitouch performance surface](./store/phone/01-expressive-multitouch-performance.png?raw=true)

![Volna YAWN synthesis engines](./store/phone/02-three-yawn-synthesis-engines.png?raw=true)

![Volna custom scale editor](./store/phone/03-design-your-own-scales.png?raw=true)

Demo video: <https://youtu.be/Cf_wpDYhudk>

---

## Contents

- [Performance surface](#performance-surface)
- [Sound engines](#sound-engines)
- [Effect chains](#effect-chains)
- [Microtonal tuning and MPE](#microtonal-tuning-and-mpe)
- [Touch pressure](#touch-pressure)
- [Motion and touch modulation matrix](#motion-and-touch-modulation-matrix)
- [Wavetables and VWaveEditor import](#wavetables-and-vwaveeditor-import)
- [YAWN integration over RTP-MIDI](#yawn-integration-over-rtp-midi)
- [MIDI controller and device support](#midi-controller-and-device-support)
- [Settings, persistence, and lifecycle](#settings-persistence-and-lifecycle)
- [Recording and playback](#recording-and-playback)
- [DSP meter and performance](#dsp-meter-and-performance)
- [Architecture](#architecture)
- [Project layout](#project-layout)
- [Build and test](#build-and-test)
- [Testing and CI](#testing-and-ci)
- [Known limitations and roadmap](#known-limitations-and-roadmap)
- [Credits and licenses](#credits-and-licenses)

---

## Performance surface

- Landscape, fullscreen, multitouch surface with up to 10 simultaneous touches.
- Full-bleed performance area: the grid and waveform extend behind the dim
  top strip, so the playing surface reaches the screen edges. The strip carries
  the wordmark, note/scale readout, engine, display mode, and DSP meter.
- Horizontal position selects notes; the **Pitch bend** slider adds optional
  microtonal bend and defaults to 0%. Vertical position selects a register
  (1–8, default 3); each register has its own offset and is distinguished by a
  color plus a subtle hatch pattern.
- Touch X/Y and Touch ΔX/ΔY are bipolar modulation sources in the MOD matrix;
  they follow the most recently moved finger.
- The note grid is drawn edge-to-edge, with cell boundaries and colored
  register separators. **Grid size** sets how many pixels of horizontal travel
  are needed to reach the next note.
- In TET12, note names are drawn inside each register zone and again along the
  bottom edge of the grid. Non-TET12 grids show scale-degree numbers. Labels
  are hidden when cells or zones become too narrow, and they follow the current
  root, scale, tuning, and register offsets.
- Active-touch labels use an offset callout chip with a leader line, so the note
  text is not hidden under the finger.
- Finger pressure drives note velocity when the touchscreen exposes pressure.
- The performance display has a stable-trigger waveform with a gradient fill,
  a 64-band log-spaced spectrum overlay, peak caps, and note-name chips for
  active touches. The **WAVE / FFT / BOTH** control is remembered between
  launches.
- The **SET** button opens the tabbed settings panel; **REC** and **PLAY**
  control the built-in loop recorder. A compact pie in the rail shows record
  buffer fill or playback position, and **CLEAR** resets the loop.
- The app keeps the screen awake and is locked to landscape.

## Sound engines

Four engines can be selected at runtime.

### Volna Classic

- The original 10-voice dual-wavetable engine.
- 256 wavetables, each 256 samples long, morphing from sine through triangle,
  square, saw, and filtered noise.
- Two oscillators per voice, per-oscillator ADSR, oscillator spread, and delay.
- Kept for the original character and for users who prefer the classic controls.
- Supports custom `.vwt` imports by resampling them into the 256-slot morph bank.

### YAWN Wavetable

- YAWN’s 16-voice wavetable synthesizer.
- Built-in banks: Basic (sine → triangle → saw → square → pulse), PWM, Formant,
  Harmonic, and Digital.
- Frame morphing with the **Position** parameter.
- State-variable filter with cutoff, resonance, and filter envelope.
- Amp ADSR, sub oscillator, unison detune, LFO-to-position, and master volume.
- Loads VWaveEditor `.vwt` v1/v2 files directly into its Custom table slot.

### YAWN Subtractive

- YAWN’s virtual-analog synthesizer.
- Two oscillators plus sub oscillator and noise.
- Oscillator waveforms, levels, detune, octave, and sub/noise mix.
- LP/BP/HP state-variable filter with envelope and resonance.
- Amp ADSR, filter ADSR, LFO, and master volume.

### YAWN Karplus-Strong

- YAWN’s physical-modelling plucked/struck string synthesizer.
- Noise, bright-noise, saw, and sine exciters.
- Damping, brightness, decay, body resonance, and inharmonic string stretch.
- Attack and volume controls with 16-voice polyphony.
- Per-note pitch bend and the modulation matrix pitch destination are supported.

The selected YAWN instrument and its full parameter set are persisted across
launches. All YAWN instrument banks are saved, so switching among Wavetable,
Subtractive, and Karplus-Strong does not lose another instrument’s settings.

## Effect chains

The YAWN engine hosts a MIDI-effect chain followed by the instrument and an
audio-effect chain. Both chains are editable at runtime.

### MIDI effects

| Effect | Purpose |
|---|---|
| Arpeggiator | Direction, rate, gate, octaves, swing, and free-running clock |
| Chord | Up to six parallel intervals with velocity scaling |
| Scale | Scale-quantised output |
| Note Length | Gate-length shaping |
| Velocity | Velocity scaling/compression |
| Random | Random note/velocity variation |
| Pitch | Semitone/octave transposition |
| LFO | LFO modulator slot (modulation routing is not yet wired) |

### Audio effects

| Effect | Purpose |
|---|---|
| Delay | Time, feedback, wet/dry, ping-pong, feedback filter, tempo sync |
| Chorus | Rate, depth, wet/dry, voices |
| Reverb | Room size, damping, wet/dry, pre-delay, width |
| Filter | LP/HP/BP/notch with cutoff and resonance |
| Distortion | Soft clip, hard clip, tube, foldback, tone, 2× oversampling |
| EQ | Three-band shelf/peak EQ |
| Compressor | Threshold, ratio, knee, attack/release, makeup gain |
| Limiter | Ceiling, lookahead, release |

### Chain editor

- Up to **8 slots per chain**, with duplicate effect types allowed.
- **Add effect** appends an effect from the catalog.
- **Up** / **Down** reorder the chain.
- **Remove** deletes a slot.
- **ON/OFF** bypasses the effect without removing it.
- **Params** expands the effect’s own parameter sliders.
- Chain order, bypass state, and every parameter value are persisted.
- The old legacy delay sliders still work in YAWN mode: they target the first
  Delay effect in the audio chain.

## Microtonal tuning and MPE

Tuning is no longer baked into MIDI note numbers. Touch input is converted to a
continuous target pitch in standard 12-TET semitones, then split into:

- a normal MIDI note (0–127), and
- a per-note pitch-bend offset scaled by the **Pitch bend** control.

The Pitch bend control defaults to 0%, so horizontal position selects quantized
notes without detuning. Raise it to restore microtonal pitch and glissando. At
0%, legato sliding is bypassed and notes retrigger at cell boundaries.

Supported controls:

- **Root note** from C0 to B6.
- **Scales**: the TET12 built-ins plus user-defined TET12 scales created from a
  12-note editor. TET24 starts with quarter-tone chromatic, major, natural
  minor, neutral pentatonic, and 24-TET Rast/Bayati approximations. Other TETs
  fall back to chromatic.
- **A4 tuning**: 400–500 Hz.
- **TET**: 2–48 equal divisions of the octave. TET applies to all YAWN engines.
- **Octave factor**: 1.5–2.5, allowing stretched or compressed octaves.
- **Registers**: 1–8 vertical zones with independent offsets. Register 1 is the
  bottom (lowest-pitch) zone and higher registers move upward in pitch. The unit
  can be semitones or scale steps. Presets include Octaves, Power, Major, Minor, and a
  standard-guitar string layout (`0/+5/+10/+15/+19/+24` semitones) for reusing
  guitar chord shapes with a chromatic grid.

Gesture model:

- **Legato on**: with pitch bend above 0%, each touch keeps an anchor note and
  slides with up to ±48 semitones of bend. It re-anchors only when the range is
  exceeded.
- **Legato off**: the cell note follows the touch and the bend stays small,
  with note retriggering only when the touch centre crosses a vertical cell
  boundary. Legato is bypassed when Pitch bend is 0%.
- Internal voices receive per-note pitch bend, applied independently per voice.
- External MIDI uses **MPE**: one member channel per touch, channel pitch bend,
  channel pressure, and automatic lower-zone plus 48-semitone range RPNs.

## Touch pressure

- Detects `AXIS_PRESSURE` per touchscreen through `InputDevice.MotionRange`.
- When supported, pressure drives note velocity through a musical curve and is
  sent as MPE channel pressure.
- When pressure is unavailable, touch size (`AXIS_SIZE`) is used as a coarse
  expressive fallback; otherwise a fixed default velocity is used.
- Per-finger pressure is tracked independently.
- The velocity value is displayed next to the active note marker.

## Motion and touch modulation matrix

The **MOD** tab contains a small sensor/touch-to-parameter matrix. Rows are
disabled by default, and the motion sensors are only registered while at least
one enabled row uses them, so idle battery use stays low.

Sources:

- Accelerometer X/Y/Z
- Gyroscope X/Y/Z
- Magnetometer X/Y/Z
- Magnetometer magnitude
- Touch X (absolute horizontal position, -1..1)
- Touch Y (absolute vertical position, top +1 to bottom -1)
- Touch ΔX / Touch ΔY (movement since touch-down; zero at touch-down and
  reaches +/-1 after half-screen travel)

Touch sources do not require sensor hardware and follow the most recently moved
finger. For touch-relative pitch, route **Touch ΔX → Pitch**: the bend starts
at zero where the finger lands and follows horizontal movement from there.

Sensors are normalized and low-pass filtered. The first event after enabling a
sensor (or pressing **Calibrate sensors**) becomes the zero baseline, making the
controls relative to how the device is held or to the local magnetic field. The
magnetometer makes it possible to play with a magnet.

A live indicator appears both in the top strip and beside the MOD status. Its
three dots light for the active accelerometer, gyro, and magnetometer groups,
and the center needle shows the current normalized sensor value.

Targets:

- **Synth**: filter cutoff, filter resonance, wavetable position, LFO amount,
  unison detune, sub level, oscillator detune, and noise level where applicable
  to the selected YAWN instrument.
- **Performance**: Pitch. This applies per-note pitch bend to all held voices;
  a full amount spans +/- 12 semitones and is added to the touch pitch-bend
  setting.
- **Audio FX**: continuous parameters of every effect currently in the audio
  chain. Boolean and stepped parameters are excluded.

Matrix rows:

- Up to six rows, each with **ON/OFF**, source, target, and a bipolar amount
  from -100% to +100%.
- Amount is applied as a fraction of the target parameter range; multiple rows
  can target the same parameter and their offsets are summed and clamped.
- Modulation is additive in the native audio thread, so the saved knob/slider
  value stays intact and is restored when a row is disabled.
- Rows and their settings are persisted.
- The engine-switch path resets the outgoing synth/arp and re-sends held touches
  to the newly selected engine, so an active YAWN arpeggiator cannot leak into
  Volna Classic.

## Wavetables and VWaveEditor import

- Imports VWaveEditor `.vwt` **v1 and v2** files through Android’s document
  picker.
- Validates the format, dimensions, base64 data, and total size before use.
- Supports non-uniform keyframe positions; frames are linearly blended.
- Imported tables are previewed, stored in app-private storage, and restored on
  launch.
- **Restore factory bank** removes the custom table and returns to the built-ins.
- In YAWN Wavetable mode, the file is loaded directly by YAWN’s wavetable synth
  (frames are resampled to its 256-sample frames and capped at its frame count).
- In Volna Classic mode, the import is resampled and blended into the 256-slot
  morph bank.

## YAWN integration over RTP-MIDI

Volna can act as a wireless MIDI controller for YAWN.

1. Start YAWN on a computer on the same network. It listens for sessionless
   RTP-MIDI on UDP `5004` by default. Override with `YAWN_RTP_MIDI_PORT`.
2. In Volna, open settings, enter the YAWN host IP, keep the port at `5004`, and
   enable **RTP-MIDI ON**.
3. In YAWN, arm a MIDI track and route it to **All Ports** or the MPE-aware input.

Protocol details:

- RFC 6295 RTP-MIDI packets, 48 kHz timestamp units, no recovery journal.
- Sessionless: no AppleMIDI invitation/sync handshake is required.
- Notes are sent on MPE member channels 1–15; pitch bend carries the microtonal
  offset; pressure is sent as channel pressure.
- YAWN translates member-channel bends into per-note expression before routing,
  so each voice can be tuned independently.
- MPE lower-zone configuration and 48-semitone pitch-bend-range RPNs are sent
  automatically when the connection starts.

## MIDI controller and device support

- Android MIDI API input and output port selection.
- Virtual MIDI device service advertised as **Bokontep Volna** with an input
  port, so other apps can send notes/CCs to Volna.
- Incoming MIDI: note on/off, pitch bend, and control change.
- Outgoing MIDI: notes, pitch bend, channel pressure, MPE member channels, and
  RTP-MIDI.
- Hardware MIDI port lists are rescanned on launch.

## Settings, persistence, and lifecycle

Settings open in a side panel beside the performance surface, so you can keep
playing while editing. The panel has six tabs, each with collapsible cards:

- **Play**: grid size, DSP peak reset, legato, output limiter, polyphony
  leveling, red-phosphor display option, and About/credits.
- **Sound**: engine selector, YAWN instrument parameters, tempo/master, classic
  oscillator, and wavetable bank.
- **FX**: editable MIDI and audio effect chains plus the legacy delay.
- **Tune**: TET, root, scale editor, A4, octave factor, pitch bend, register
  count, offset unit/preset, and per-register offsets.
- **I/O**: Android MIDI devices and RTP-MIDI host/port/status.
- **MOD**: the sensor modulation matrix.

The selected tab is remembered. Controls use consistent label/value rows,
accent-tinted sliders, compact toggles, and larger touch targets.

![Volna modulation matrix](./store/phone/05-modulate-with-motion-and-touch.png?raw=true)

Persisted state:

- Engine selection, YAWN instrument, tempo, master volume.
- All YAWN instrument parameter banks.
- Every MIDI/audio effect chain: composition, order, bypass, and parameters.
- Tuning, built-in/custom scale selection, root, register count/unit/offsets,
  grid size, legato, pitch bend, red phosphor, scope display mode, selected
  settings tab, output limiter, and polyphony leveling.
- Imported wavetable bytes and display name.
- RTP-MIDI host/port/enabled state.
- Modulation matrix rows, sources, targets, amounts, and enable states.
- Legacy oscillator, envelope, wave-control, and delay settings.

State is saved after edits via a short debounce and on pause/destroy. It is
restored on launch and when switching back to a YAWN engine. Corrupt state is
discarded and defaults are used.

Audio lifecycle:

- The Oboe stream is stopped on pause and reopened on resume.
- If Android closes or disconnects the stream while the app is idle, the engine
  recreates it on window focus, on touch, and through delayed resume retries.

## Recording and playback

- **REC** records the final mixed output into a 60-second loop buffer.
- **PLAY** loops the recording.
- Recording and playback are available in both Classic and YAWN engines.
- A **20 ms linear crossfade** joins the end of the loop to its beginning, so the
  seam is continuous and the head is not repeated after the crossfade.
- The rail pie shows recording buffer fill (red) or playback position (accent).
- **CLEAR** stops recording/playback and empties the loop buffer.
- Recording stops automatically at 60 seconds; the pie shows a full buffer and
  the REC button is released.
- Pressing PLAY without a recorded loop is rejected instead of leaving a stuck
  PLAY state.
- Loop playback is added after the synth/effect chain but before master volume.

## DSP meter and performance

The top bar shows real-time DSP callback load and peak load. The reset button
is on the Play tab.

Two independent safety controls are available on the Play tab and are enabled
by default:

- **Output limiter**: a dedicated final master limiter with a -1 dBFS ceiling
  and 3 ms lookahead. It is independent of the editable effect chain, so it
  still protects the output if the chain is changed or bypassed.
- **Polyphony leveling**: reduces gain as more notes are held, using a smoothed
  `1 / sqrt(active voices)` curve. Single notes keep their full level while
  dense chords sum more evenly.

Both settings are persisted. The limiter adds a few milliseconds of lookahead
latency; turning it off restores the unprocessed output path.

Measured on the test device:

- Idle, default chain (3 MIDI + 4 audio effects, all bypassed): about **5%**.
- One held note, default chain: about **12%** average.
- All **16** effect slots enabled: about **41% average**, with peaks above the
  callback budget (about 146% measured). At that load dropouts are possible.

Headroom is comfortable for normal 3–5 effect chains. Reverb is the most
expensive audio effect. Use the meter and keep the chain moderate when targeting
glitch-free low-latency audio.

## Architecture

```
Android UI (Java)
  ├─ MainActivity / settings / touch surface
  ├─ VwtWavetable parser + WavetableStore
  ├─ SynthEngine JNI facade
  └─ RtpMidiClient (RTP-MIDI sender)
            │ JNI
            ▼
C++ engine (NDK)
  ├─ EngineEventQueue (multi-producer, lock-free audio consumer)
  ├─ YawnEngine
  │   ├─ MIDI effect chain
  │   ├─ Wavetable / Subtractive / Karplus-Strong instrument
  │   ├─ audio effect chain
  │   ├─ loop recorder
  │   └─ JSON state save/restore
  ├─ Volna Classic VAEngine
  └─ Oboe low-latency output
            │
            ▼
Vendored YAWN DSP (instruments, MIDI effects, audio effects)
```

Key design choices:

- UI/MIDI events are queued and applied on the audio thread.
- Wavetable banks and effect chains are mutated with the stream stopped.
- YAWN instruments apply per-note pitch bend per voice.
- Effect chain state is versioned JSON, persisted by the UI layer.
- Oboe stream recovery handles closed/disconnected/stale states.

## Project layout

```
app/src/main/java/org/bokontep/wavesynth/
  MainActivity.java          touch surface, settings, chain editor
  SynthEngine.java           JNI facade
  VwtWavetable.java          .vwt parser and 256-slot mapper
  WavetableStore.java        imported wavetable persistence
  MicrotonalPitchMapper.java continuous pitch + note/bend split
  ScaleDefinition.java       TET-aware scale definition
  ScaleStore.java            built-in and custom scale persistence
  RegisterLayout.java        configurable register count/units/offsets
  MicrotonalVoice.java       hybrid anchor/re-anchor gesture state
  TouchExpression.java       pressure/velocity curve
  RtpMidiClient.java         RTP-MIDI sender
  Scope.java                 waveform/spectrum display
  WaveDisplay.java           oscillator preview
  SpectrumAnalyzer.java      1024-point FFT and log-spaced bars
  LoopMeterView.java         loop fill/playback pie
  SensorModulation.java      sensor normalization and calibration
  ModMatrixController.java   MOD tab matrix UI and persistence
  CollapsibleSection.java    settings section UI
app/src/main/cpp/
  include/                   VAEngine, YawnEngine, event queue, DSP headers
  src/                       classic AD/ADSR
  yawn_dsp/                  vendored YAWN DSP subset + license
  wavesynth-lib.cpp          JNI boundary
app/src/test/                JVM unit tests
scripts/sync_yawn_dsp.sh     refresh vendored YAWN DSP
scripts/check_16kb_page_size.sh  release APK/AAB page-size verification
native-tests/               host-compiled YAWN DSP tests
store/                      Play Store icon, feature graphic, and screenshots
.github/workflows/android.yml CI build, tests, and signed releases
```

## Build and test

Requirements:

- JDK 17
- Android SDK (compileSdk 34)
- Android NDK `27.2.12479018`
- CMake `3.22.1`

```sh
git clone --recursive https://github.com/tkleisas/volna.git
cd volna
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./native-tests/run.sh
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
You can also open the project in Android Studio, install the NDK above, and
build the `app` configuration. The Play application ID is
`gr.tkleisas.volna`; the Java namespace stays `org.bokontep.wavesynth` for
source and JNI compatibility.

Release builds read signing values from `~/.volna/keystore.properties` (or the
`RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and
`RELEASE_KEY_PASSWORD` environment variables). The keystore and properties file
are deliberately kept outside the repository.

```sh
./gradlew :app:assembleRelease :app:bundleRelease
```

Signed release artifacts are built for tags matching `v*`. CI verifies the APK
and AAB signatures, checks that the 64-bit native libraries use 16 KB page
alignment, and attaches both files to the GitHub release.

## Testing and CI

The project follows test-driven development; the workflow and commands are
documented in [AGENTS.md](./AGENTS.md). New behavior and bug fixes should start
with a focused failing test.

- JVM unit tests cover `.vwt` parsing/mapping, microtonal pitch splitting and
  cell boundaries, hybrid gesture transitions, touch-relative modulation,
  pressure curves, RTP-MIDI packet layout, and the spectrum/FFT analyzer.
- Robolectric UI tests cover preferences/wavetable persistence, custom view
  state, main-layout inflation, and modulation-matrix source/target interaction.
- Native C++ tests cover MIDI conversion, MIDI pitch transpose/flush, scale
  quantization, pitch-modulation note tracking, envelopes, and the YAWN
  Wavetable, Subtractive, and Karplus-Strong engines via
  `native-tests/run.sh`.
- Scale, register-layout, persistence, and pitch-mapping tests cover the custom
  scale and configurable-register behavior.
- GitHub Actions runs unit tests, lint, and the debug build on every push and
  pull request. Tagged builds also produce signed APK/AAB release assets and
  verify 16 KB page-size compatibility.

## Known limitations and roadmap

- RTP-MIDI is sessionless; AppleMIDI session negotiation is not implemented.
- External MIDI uses MPE, not a true MIDI 2.0 UMP transport. Internally the
  engine uses per-note pitch bend.
- The LFO is present in the MIDI effect catalog, but modulation routing to
  instrument/effect parameters is not wired yet.
- Effect chains support 8 slots each; the CPU can be overloaded when all 16 are
  enabled simultaneously.
- Hardware MIDI port selections are re-scanned at launch rather than persisted.
- The modulation matrix has six slots; sensor values are intentionally smoothed,
  so extremely fast gestures are softened.
- Stereo output only.
- No plugin hosting inside Volna.

Future work: LFO modulation routing, MIDI 2.0 UMP transport, MIDI port
persistence, effect presets, and per-effect serialisation beyond the current
chain state.

## Credits and licenses

- Volna: MIT.
- YAWN DSP subset: MIT; see `app/src/main/cpp/yawn_dsp/NOTICE.md` and
  `LICENSE-yawn.txt`.
- nlohmann/json: MIT.
- Google Oboe: Apache 2.0.
- VWaveEditor: `.vwt` format and wavetable tooling.
- Volna is the Bokontep music project. Original project, sound design, and
  identity by Bokontep: <https://github.com/bokontep> ·
  <https://www.bokontep.gr>.

---
