package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SandboxSlotAllocatorTest
{
    @Test
    void allocatesNonOverlappingGridSlotsAndReusesReleasedSlot()
    {
        SandboxSlotAllocator allocator = new SandboxSlotAllocator(512, 2);
        SandboxSlot first = allocator.allocate();
        SandboxSlot second = allocator.allocate();
        SandboxSlot third = allocator.allocate();

        assertEquals(0, first.index());
        assertEquals(512, second.originX());
        assertEquals(512, third.originZ());
        assertTrue(allocator.release(second.index()));
        assertFalse(allocator.isAllocated(second.index()));
        assertEquals(second.index(), allocator.allocate().index());
    }
}
