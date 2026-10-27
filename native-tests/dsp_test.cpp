#include "PitchModulation.h"
#include "instruments/Envelope.h"
#include "instruments/KarplusStrong.h"
#include "instruments/SubtractiveSynth.h"
#include "instruments/WavetableSynth.h"
#include "midi/MidiPitch.h"
#include "midi/MidiTypes.h"
#include "midi/Scale.h"

#include <cmath>
#include <cstdio>
#include <vector>

using volna::PitchModulation;
using yawn::instruments::Envelope;
using yawn::instruments::KarplusStrong;
using yawn::instruments::SubtractiveSynth;
using yawn::instruments::WavetableSynth;
using yawn::midi::MidiBuffer;
using yawn::midi::MidiMessage;
using yawn::midi::MidiPitch;
using yawn::midi::Scale;
using yawn::midi::TransportInfo;

namespace {
int failures = 0;

void check(bool condition, const char* message) {
    if (!condition) {
        std::printf("FAIL: %s\n", message);
        ++failures;
    }
}

void checkNear(double actual, double expected, double tolerance,
               const char* message) {
    if (std::fabs(actual - expected) > tolerance) {
        std::printf("FAIL: %s (actual %.6f expected %.6f)\n",
                    message, actual, expected);
        ++failures;
    }
}

void testMidiConversions() {
    using namespace yawn::midi;
    check(Convert::vel7to16(0) == 0, "velocity zero converts to zero");
    check(Convert::vel7to16(127) == 65535,
          "velocity 127 converts to full scale");
    check(Convert::cc7to32(0) == 0, "CC zero converts to zero");
    check(Convert::cc7to32(127) == 0xFFFFFFFFu,
          "CC 127 converts to full scale");
    check(Convert::pb14to32(8192) == 0x80000000u,
          "center pitch bend maps to 32-bit center");
    check(Convert::pb32to14(0x80000000u) == 8192,
          "32-bit center maps back to 14-bit center");
    const uint32_t center = Convert::floatToPb32(0.0f);
    check(center == 0x80000000u, "zero bend maps to 32-bit center");
    checkNear(Convert::pb32toFloat(center), 0.0, 1.0e-6,
              "center bend decodes to zero");
}

MidiBuffer noteBuffer(MidiMessage message) {
    MidiBuffer buffer;
    buffer.addMessage(message);
    return buffer;
}

void testMidiPitchTransposeAndNoteOff() {
    MidiPitch pitch;
    pitch.init(48000.0);
    pitch.setParameter(MidiPitch::kSemitones, 12);
    pitch.reset();

    MidiBuffer on = noteBuffer(MidiMessage::noteOn(2, 60, 100));
    pitch.process(on, 256, TransportInfo{});
    check(on.count() == 1, "pitch note-on emits one message");
    check(on[0].note == 72, "pitch transposes note-on by an octave");

    MidiBuffer off = noteBuffer(MidiMessage::noteOff(2, 60));
    pitch.process(off, 256, TransportInfo{});
    check(off.count() == 1, "pitch note-off emits one message");
    check(off[0].note == 72, "pitch note-off follows the transposed note");
}

void testMidiPitchFlushesHeldNotesWhenChanged() {
    MidiPitch pitch;
    pitch.init(48000.0);
    pitch.reset();

    MidiBuffer on = noteBuffer(MidiMessage::noteOn(0, 60, 100));
    pitch.process(on, 256, TransportInfo{});
    check(on[0].note == 60, "untransposed note-on stays at input pitch");

    pitch.setParameter(MidiPitch::kSemitones, 7);
    MidiBuffer flush;
    pitch.process(flush, 256, TransportInfo{});
    check(flush.count() == 1, "pitch change flushes held note(s)");
    check(flush[0].type == MidiMessage::Type::NoteOff,
          "pitch flush emits note-off");
    check(flush[0].note == 60, "pitch flush uses the old output note");
}

void testScaleQuantization() {
    Scale scale;
    scale.init(48000.0);
    scale.setParameter(Scale::kRoot, 0);
    scale.setParameter(Scale::kScaleType, Scale::Major);

    check(scale.quantize(60) == 60, "scale leaves C alone");
    check(scale.quantize(61) == 60, "scale lowers C# to C");
    check(scale.quantize(63) == 62, "scale lowers D# to D");
    check(scale.quantize(64) == 64, "scale leaves E alone");

    MidiBuffer buffer = noteBuffer(MidiMessage::noteOn(0, 61, 100));
    scale.process(buffer, 256, TransportInfo{});
    check(buffer[0].note == 60, "scale effect quantizes note-on");
}

void testPitchModulationTargetAndActiveNotes() {
    PitchModulation pitch;
    checkNear(pitch.offsetSemitones(), 0.0, 1.0e-6,
              "pitch modulation starts at zero");

    pitch.setNormalizedOffset(0.5f);
    checkNear(pitch.offsetSemitones(), 6.0, 1.0e-6,
              "normalized pitch offset maps to semitones");
    check(pitch.consumeDirty(), "pitch offset change marks dirty");

    pitch.noteOn(1, 60);
    int channels[PitchModulation::kChannels * PitchModulation::kNotes];
    int notes[PitchModulation::kChannels * PitchModulation::kNotes];
    float bends[PitchModulation::kChannels * PitchModulation::kNotes];
    int count = pitch.collectUpdates(channels, notes, bends,
            PitchModulation::kChannels * PitchModulation::kNotes);
    check(count == 1, "one active note yields one pitch update");
    check(channels[0] == 1 && notes[0] == 60,
          "pitch update carries channel and note");
    checkNear(bends[0], 6.0, 1.0e-6,
              "new note includes the current modulation offset");

    pitch.setBaseBend(1, 60, 0.5f);
    count = pitch.collectUpdates(channels, notes, bends,
            PitchModulation::kChannels * PitchModulation::kNotes);
    checkNear(bends[0], 6.5, 1.0e-6,
              "base bend and modulation are added");

    pitch.setNormalizedOffset(5.0f);
    checkNear(pitch.offsetSemitones(), 48.0, 1.0e-6,
              "pitch modulation clamps at the bend range");
    count = pitch.collectUpdates(channels, notes, bends,
            PitchModulation::kChannels * PitchModulation::kNotes);
    checkNear(bends[0], 48.0, 1.0e-6,
              "per-note pitch is clamped after modulation");

    pitch.noteOff(1, 60);
    count = pitch.collectUpdates(channels, notes, bends,
            PitchModulation::kChannels * PitchModulation::kNotes);
    check(count == 0, "released notes are removed from pitch updates");

    pitch.markActive(2, 64);
    count = pitch.collectUpdates(channels, notes, bends,
            PitchModulation::kChannels * PitchModulation::kNotes);
    check(count == 1 && channels[0] == 2 && notes[0] == 64,
          "generated notes become pitch-modulation targets");
    check(pitch.consumeDirty(), "new active note marks pitch dirty");

    pitch.reset();
    checkNear(pitch.offsetSemitones(), 0.0, 1.0e-6,
              "reset clears the pitch offset");
    count = pitch.collectUpdates(channels, notes, bends,
            PitchModulation::kChannels * PitchModulation::kNotes);
    check(count == 0, "reset clears active pitch notes");
}

void testEnvelopeReachesSustainAndReleases() {
    Envelope envelope;
    envelope.setSampleRate(48000.0);
    envelope.setADSR(0.001f, 0.001f, 0.5f, 0.001f);
    envelope.reset();
    envelope.gate(true);

    for (int i = 0; i < 1000; ++i) {
        envelope.process();
    }
    checkNear(envelope.level(), 0.5, 0.02, "envelope reaches sustain");
    check(!envelope.isIdle(), "envelope is active before release");

    envelope.gate(false);
    for (int i = 0; i < 5000; ++i) {
        envelope.process();
    }
    check(envelope.isIdle(), "envelope reaches idle after release");
    checkNear(envelope.level(), 0.0, 1.0e-6, "released envelope is silent");
}

void testKarplusStrongRender() {
    KarplusStrong synth;
    synth.init(48000.0, 256);
    check(std::string(synth.name()) == "Karplus-Strong",
          "Karplus-Strong has the expected name");
    check(std::string(synth.id()) == "karplus",
          "Karplus-Strong has the expected id");

    MidiBuffer midi = noteBuffer(MidiMessage::noteOn(0, 60, 100));
    std::vector<float> buffer(512, 0.0f);
    synth.process(buffer.data(), 256, 2, midi);
    double peak = 0.0;
    bool finite = true;
    for (float sample : buffer) {
        peak = std::max(peak, static_cast<double>(std::fabs(sample)));
        finite = finite && std::isfinite(sample);
    }
    check(peak > 0.001, "Karplus-Strong produces audio");
    check(finite, "Karplus-Strong output is finite");

    MidiBuffer bend;
    bend.addMessage(MidiMessage::perNotePitchBend(
            0, 60, yawn::midi::Convert::floatToPb32(0.5f / 48.0f)));
    std::fill(buffer.begin(), buffer.end(), 0.0f);
    synth.process(buffer.data(), 256, 2, bend);
    finite = true;
    for (float sample : buffer) {
        finite = finite && std::isfinite(sample);
    }
    check(finite, "Karplus-Strong handles per-note pitch bend");
}

void checkSynthRender(const char* name, bool wavetable) {
    std::vector<float> buffer(512, 0.0f);
    MidiBuffer midi = noteBuffer(MidiMessage::noteOn(0, 60, 100));
    double peak = 0.0;
    bool finite = true;

    if (wavetable) {
        WavetableSynth synth;
        synth.init(48000.0, 256);
        synth.setParameter(WavetableSynth::kAmpAttack, 0.001f);
        synth.setParameter(WavetableSynth::kAmpSustain, 0.8f);
        synth.process(buffer.data(), 256, 2, midi);
    } else {
        SubtractiveSynth synth;
        synth.init(48000.0, 256);
        synth.setParameter(SubtractiveSynth::kAmpAttack, 0.001f);
        synth.setParameter(SubtractiveSynth::kAmpSustain, 0.8f);
        synth.process(buffer.data(), 256, 2, midi);
    }

    for (float sample : buffer) {
        peak = std::max(peak, static_cast<double>(std::fabs(sample)));
        finite = finite && std::isfinite(sample);
    }
    check(peak > 0.001, name);
    check(finite, "synth output is finite");
}

} // namespace

int main() {
    testMidiConversions();
    testMidiPitchTransposeAndNoteOff();
    testMidiPitchFlushesHeldNotesWhenChanged();
    testScaleQuantization();
    testPitchModulationTargetAndActiveNotes();
    testEnvelopeReachesSustainAndReleases();
    checkSynthRender("wavetable synth produces audio", true);
    checkSynthRender("subtractive synth produces audio", false);
    testKarplusStrongRender();

    if (failures == 0) {
        std::printf("native DSP tests passed\n");
        return 0;
    }
    std::printf("native DSP failures=%d\n", failures);
    return 1;
}
