#pragma once

#include <algorithm>
#include <cmath>

namespace volna {

/**
 * Tracks the global pitch-modulation offset and each active note's base bend.
 * Kept free of Android/Oboe so the pitch-modulation path is host-testable.
 */
class PitchModulation {
public:
    static constexpr int kChannels = 16;
    static constexpr int kNotes = 128;
    static constexpr float kRangeSemitones = 12.0f;
    static constexpr float kBendRangeSemitones = 48.0f;

    void reset() {
        resetNotes();
        offsetSemitones_ = 0.0f;
        dirty_ = false;
    }

    void resetNotes() {
        for (auto& channel : active_) {
            std::fill_n(channel, kNotes, false);
        }
        for (auto& channel : baseBend_) {
            std::fill_n(channel, kNotes, 0.0f);
        }
        dirty_ = false;
    }

    void setNormalizedOffset(float normalized) {
        const float semitones = std::clamp(
                normalized * kRangeSemitones,
                -kBendRangeSemitones, kBendRangeSemitones);
        if (std::fabs(semitones - offsetSemitones_) < 0.001f) {
            return;
        }
        offsetSemitones_ = semitones;
        dirty_ = true;
    }

    float offsetSemitones() const { return offsetSemitones_; }

    bool consumeDirty() {
        const bool dirty = dirty_;
        dirty_ = false;
        return dirty;
    }

    void noteOn(int channel, int note) {
        if (!valid(channel, note)) {
            return;
        }
        baseBend_[channel][note] = 0.0f;
        active_[channel][note] = true;
        dirty_ = true;
    }

    void markActive(int channel, int note) {
        if (!valid(channel, note) || active_[channel][note]) {
            return;
        }
        active_[channel][note] = true;
        dirty_ = true;
    }

    void noteOff(int channel, int note) {
        if (!valid(channel, note)) {
            return;
        }
        active_[channel][note] = false;
        baseBend_[channel][note] = 0.0f;
    }

    void setBaseBend(int channel, int note, float semitones) {
        if (!valid(channel, note)) {
            return;
        }
        baseBend_[channel][note] = std::clamp(
                semitones, -kBendRangeSemitones, kBendRangeSemitones);
    }

    float baseBend(int channel, int note) const {
        return valid(channel, note) ? baseBend_[channel][note] : 0.0f;
    }

    bool isActive(int channel, int note) const {
        return valid(channel, note) && active_[channel][note];
    }

    /** Fills parallel arrays with active notes and their base+mod pitch. */
    int collectUpdates(int* channels, int* notes, float* semitones,
                       int capacity) const {
        if (capacity <= 0) {
            return 0;
        }
        int count = 0;
        for (int channel = 0; channel < kChannels; ++channel) {
            for (int note = 0; note < kNotes; ++note) {
                if (!active_[channel][note]) {
                    continue;
                }
                if (count >= capacity) {
                    return count;
                }
                channels[count] = channel;
                notes[count] = note;
                semitones[count] = std::clamp(
                        baseBend_[channel][note] + offsetSemitones_,
                        -kBendRangeSemitones, kBendRangeSemitones);
                ++count;
            }
        }
        return count;
    }

private:
    static bool valid(int channel, int note) {
        return channel >= 0 && channel < kChannels
                && note >= 0 && note < kNotes;
    }

    bool active_[kChannels][kNotes]{};
    float baseBend_[kChannels][kNotes]{};
    float offsetSemitones_ = 0.0f;
    bool dirty_ = false;
};

} // namespace volna
