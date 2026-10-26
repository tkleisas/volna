#ifndef EngineEventQueue_h_
#define EngineEventQueue_h_

#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>

struct EngineEvent {
    enum class Type : uint8_t {
        NoteOn,
        NoteOff,
        NotePitch,
        NoteTransition,
        NoteSpread,
        PitchBend,
        ControlChange,
        SelectWaveform,
        SetTet,
        SetTune,
        SetOctaveFactor,
        SetOsc1Wave,
        SetOsc2Wave,
        SetAdsr,
        SetPwm,
        SetDelayLevel,
        SetDelayLength,
        SetDelayFeedback,
        SetPlay,
        SetRecord,
        PolyPressure,
        ChannelPressure,
        AllNotesOff,
        SetInstrument,
        SetInstrumentParam,
        SetMidiEffectParam,
        SetMidiEffectBypass,
        SetAudioEffectParam,
        SetAudioEffectBypass,
        SetTempo,
        SetMasterVolume,
        SetSafetyLimiter,
        SetPolyphonyLeveling,
        ClearLoop,
        SetModSlot,
    };

    Type type = Type::NoteOn;
    uint8_t channel = 0;
    uint8_t a = 0;
    uint8_t b = 0;
    uint8_t c = 0;
    uint8_t d = 0;
    int32_t i = 0;
    int32_t j = 0;
    float f = 0.0f;
};

// Multi-producer, single-consumer queue. Control threads (UI and MIDI)
// push events under a short producer mutex; the audio callback pops
// without locking. If the queue fills up, new events are dropped rather
// than blocking the caller.
class EngineEventQueue {
public:
    static constexpr uint32_t kCapacity = 2048;
    static_assert((kCapacity & (kCapacity - 1)) == 0,
                  "capacity must be a power of two");

    bool push(const EngineEvent& event) {
        std::lock_guard<std::mutex> lock(mProducerMutex);
        const uint32_t head = mHead.load(std::memory_order_relaxed);
        const uint32_t tail = mTail.load(std::memory_order_acquire);
        if (head - tail >= kCapacity) {
            return false;
        }
        mEvents[head & (kCapacity - 1)] = event;
        mHead.store(head + 1, std::memory_order_release);
        return true;
    }

    bool pop(EngineEvent& event) {
        const uint32_t tail = mTail.load(std::memory_order_relaxed);
        const uint32_t head = mHead.load(std::memory_order_acquire);
        if (tail == head) {
            return false;
        }
        event = mEvents[tail & (kCapacity - 1)];
        mTail.store(tail + 1, std::memory_order_release);
        return true;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mProducerMutex);
        const uint32_t head = mHead.load(std::memory_order_acquire);
        mTail.store(head, std::memory_order_release);
    }

private:
    std::array<EngineEvent, kCapacity> mEvents{};
    std::atomic<uint32_t> mHead{0};
    std::atomic<uint32_t> mTail{0};
    std::mutex mProducerMutex;
};

#endif
