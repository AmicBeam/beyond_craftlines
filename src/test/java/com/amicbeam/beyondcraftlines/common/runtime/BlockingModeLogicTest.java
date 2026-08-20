package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockingModeLogicTest
{
    @Test
    void matchesAe2PatternProviderSemantics()
    {
        assertTrue(BlockingModeLogic.shouldWait(true, true));
        assertFalse(BlockingModeLogic.shouldWait(true, false));
        assertFalse(BlockingModeLogic.shouldWait(false, true));
        assertFalse(BlockingModeLogic.shouldWait(false, false));
    }

    @Test
    void blockingDispatchesExactlyOneCraftAtATime()
    {
        assertEquals(1, BlockingModeLogic.craftsToDispatch(true, 2));
        assertEquals(1, BlockingModeLogic.amountToDispatch(true, 2, 2));
        assertEquals(2, BlockingModeLogic.amountToDispatch(true, 3, 2));
    }

    @Test
    void disabledBlockingDispatchesTheWholeStep()
    {
        assertEquals(2, BlockingModeLogic.craftsToDispatch(false, 2));
        assertEquals(2, BlockingModeLogic.amountToDispatch(false, 2, 2));
    }
}
