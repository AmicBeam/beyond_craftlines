package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class VirtualCraftingThrottleTest
{
    @Test
    void defaultTwentyTickCadenceAllowsOneNodeAtEachBoundary()
    {
        long next = VirtualCraftingThrottle.nextAllowedTick(100, 20);
        assertEquals(120, next);
        assertFalse(VirtualCraftingThrottle.ready(119, next));
        assertTrue(VirtualCraftingThrottle.ready(120, next));
    }

    @Test
    void nextTickSaturatesInsteadOfOverflowing()
    {
        assertEquals(Long.MAX_VALUE, VirtualCraftingThrottle.nextAllowedTick(Long.MAX_VALUE - 5, 20));
    }
}
