#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${TMPDIR:-/tmp}"
SOURCE_DIR="$ROOT/app/src/main/cpp/yawn_dsp"

g++ -std=c++17 -O2 \
    -I "$ROOT/app/src/main/cpp/include" \
    -I "$SOURCE_DIR/src" \
    -I "$SOURCE_DIR/third_party" \
    "$ROOT/native-tests/dsp_test.cpp" \
    "$SOURCE_DIR/src/midi/MidiPitch.cpp" \
    "$SOURCE_DIR/src/midi/Scale.cpp" \
    "$SOURCE_DIR/src/instruments/WavetableSynth.cpp" \
    "$SOURCE_DIR/src/instruments/SubtractiveSynth.cpp" \
    "$SOURCE_DIR/src/instruments/KarplusStrong.cpp" \
    -o "$OUT_DIR/volna_dsp_test"

"$OUT_DIR/volna_dsp_test"
