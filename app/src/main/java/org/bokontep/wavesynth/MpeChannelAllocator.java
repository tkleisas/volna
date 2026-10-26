package org.bokontep.wavesynth;

/** Allocates MPE member channels 1-15, leaving channel 0 as the master. */
public final class MpeChannelAllocator {
    public static final int FIRST_CHANNEL = 1;
    public static final int LAST_CHANNEL = 15;
    private final boolean[] used = new boolean[16];

    public int allocate() {
        for (int channel = FIRST_CHANNEL; channel <= LAST_CHANNEL; channel++) {
            if (!used[channel]) {
                used[channel] = true;
                return channel;
            }
        }
        return FIRST_CHANNEL;
    }

    public void release(int channel) {
        if (channel >= FIRST_CHANNEL && channel <= LAST_CHANNEL) {
            used[channel] = false;
        }
    }

    public void reset() {
        for (int channel = 0; channel < used.length; channel++) {
            used[channel] = false;
        }
    }

    public boolean isUsed(int channel) {
        return channel >= FIRST_CHANNEL && channel <= LAST_CHANNEL && used[channel];
    }
}
