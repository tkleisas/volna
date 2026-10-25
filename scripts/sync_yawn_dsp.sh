#!/usr/bin/env bash
# Refresh the portable YAWN DSP subset vendored under app/src/main/cpp/yawn_dsp.
# Usage: scripts/sync_yawn_dsp.sh [path-to-yawn-checkout]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
YAWN="${1:-$ROOT/../yawn}"
DEST="$ROOT/app/src/main/cpp/yawn_dsp/src"

if [[ ! -d "$YAWN/src/instruments" ]]; then
    echo "error: YAWN checkout not found at $YAWN" >&2
    exit 1
fi

mkdir -p "$DEST"/{audio,core,effects,instruments,midi,util}

copy() {
    local rel="$1"
    local src="$YAWN/src/$rel"
    local dst="$DEST/$rel"
    if [[ ! -f "$src" ]]; then
        echo "missing: $src" >&2
        exit 1
    fi
    mkdir -p "$(dirname "$dst")"
    cp "$src" "$dst"
}

for f in \
    core/Constants.h core/ParameterInfo.h WidgetHint.h \
    util/RtRetireList.h util/Logger.h \
    audio/Oversampling.h \
    midi/MidiTypes.h midi/MidiEffect.h \
    midi/Arpeggiator.h midi/Arpeggiator.cpp \
    midi/Chord.h midi/Chord.cpp \
    midi/Scale.h midi/Scale.cpp \
    midi/NoteLength.h midi/NoteLength.cpp \
    midi/VelocityEffect.h midi/VelocityEffect.cpp \
    midi/MidiRandom.h midi/MidiRandom.cpp \
    midi/MidiPitch.h midi/MidiPitch.cpp \
    midi/LFO.h midi/LFO.cpp \
    instruments/Instrument.h instruments/Envelope.h instruments/Oscillator.h \
    instruments/WavetableSynth.h instruments/WavetableSynth.cpp \
    instruments/SubtractiveSynth.h instruments/SubtractiveSynth.cpp \
    effects/AudioEffect.h effects/Biquad.h effects/FreqMap.h \
    effects/Delay.h effects/Delay.cpp \
    effects/Chorus.h effects/Chorus.cpp \
    effects/Reverb.h effects/Reverb.cpp \
    effects/PlateReverb.h effects/PlateReverb.cpp \
    effects/Filter.h effects/Filter.cpp \
    effects/Distortion.h effects/Distortion.cpp \
    effects/EQ.h effects/EQ.cpp \
    effects/Compressor.h effects/Compressor.cpp \
    effects/Limiter.h effects/Limiter.cpp
do
    copy "$f"
done

# YAWN relies on its PCH for core/Constants.h; add the include explicitly.
python3 - "$DEST" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
for rel in ["effects/AudioEffect.h", "instruments/Instrument.h", "midi/MidiEffect.h"]:
    p = root / rel
    text = p.read_text()
    if '#include "core/Constants.h"' not in text:
        text = text.replace('#pragma once\n', '#pragma once\n\n#include "core/Constants.h"\n', 1)
        p.write_text(text)
PY

echo "Synced YAWN DSP from $YAWN"
echo "Remember to refresh third_party/nlohmann/json.hpp from the YAWN build if YAWN bumps it."
