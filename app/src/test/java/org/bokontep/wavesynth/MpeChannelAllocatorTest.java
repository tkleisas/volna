package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpeChannelAllocatorTest {
    @Test
    public void allocatesMemberChannelsInOrder() {
        MpeChannelAllocator allocator = new MpeChannelAllocator();
        for (int channel = 1; channel <= 15; channel++) {
            assertEquals(channel, allocator.allocate());
            assertTrue(allocator.isUsed(channel));
        }
    }

    @Test
    public void fullAllocatorFallsBackToFirstChannelWithoutMarkingItAgain() {
        MpeChannelAllocator allocator = new MpeChannelAllocator();
        for (int i = 0; i < 15; i++) {
            allocator.allocate();
        }
        assertEquals(1, allocator.allocate());
        assertTrue(allocator.isUsed(1));
    }

    @Test
    public void resetReleasesEveryMemberChannel() {
        MpeChannelAllocator allocator = new MpeChannelAllocator();
        allocator.allocate();
        allocator.allocate();
        allocator.reset();

        assertFalse(allocator.isUsed(1));
        assertFalse(allocator.isUsed(2));
        assertEquals(1, allocator.allocate());
    }

    @Test
    public void invalidChannelsAreIgnored() {
        MpeChannelAllocator allocator = new MpeChannelAllocator();
        allocator.release(0);
        allocator.release(16);
        allocator.release(-1);
        assertFalse(allocator.isUsed(0));
        assertFalse(allocator.isUsed(16));
        assertFalse(allocator.isUsed(-1));
    }
}
