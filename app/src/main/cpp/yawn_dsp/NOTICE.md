# Vendored YAWN DSP

The portable DSP sources under this directory are copied from the YAWN project:

- Source: https://github.com/tkleisas/yawn
- Commit: `fe34b37` (instrument files also include the local per-note pitch-bend changes)
- License: MIT (see `LICENSE-yawn.txt`)

Included modules:

- `instruments/`: `WavetableSynth`, `SubtractiveSynth`, shared `Envelope`,
  `Oscillator`, and the `Instrument` interface.
- `effects/`: `Delay`, `Chorus`, `Reverb`, `PlateReverb`, `Filter`,
  `Distortion`, `EQ`, `Compressor`, `Limiter`, plus shared biquad/frequency
  helpers.
- `midi/`: `Arpeggiator`, `Chord`, `Scale`, `NoteLength`, `VelocityEffect`,
  `MidiRandom`, `MidiPitch`, `LFO`, and the `MidiEffect`/`MidiTypes` base.
- `core/`, `util/`, `audio/`, `WidgetHint.h`: shared headers required by the
  modules above.
- `third_party/nlohmann/json.hpp`: the single-header nlohmann/json
  (MIT), required by YAWN's instrument/effect preset interfaces.

Local changes are limited to build integration: `core/Constants.h` is included
explicitly by `AudioEffect.h`, `Instrument.h`, and `MidiEffect.h` because YAWN
normally provides it through its precompiled header. DSP code is otherwise
unchanged. Use `scripts/sync_yawn_dsp.sh` to refresh the vendored subset.
