package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ExternalOrderLogicTest
{
    @Test
    void exposesOnlyMachineOutputBeyondExistingBaseline()
    {
        assertEquals(0, ExternalOrderLogic.availableMachineOutput(5, 4));
        assertEquals(0, ExternalOrderLogic.availableMachineOutput(5, 5));
        assertEquals(3, ExternalOrderLogic.availableMachineOutput(5, 8));
    }

    @Test
    void rejectsInvalidCounts()
    {
        assertEquals(0, ExternalOrderLogic.availableMachineOutput(-1, 1));
        assertEquals(0, ExternalOrderLogic.availableMachineOutput(0, -1));
    }

    @Test
    void partialMachineInsertionLeavesOnlyUndeliveredInput()
    {
        assertEquals(6, ExternalOrderLogic.remainingInput(10, 4, 4));
        assertEquals(8, ExternalOrderLogic.remainingInput(10, 4, 2));
        assertEquals(10, ExternalOrderLogic.remainingInput(10, 2, 3));
    }

    @Test
    void creditsOnlyNewNetworkOutputAndNeverDoubleCountsIt()
    {
        var first = ExternalOrderLogic.creditNetworkOutput(10, 13, 0, 0, 8);
        assertEquals(3, first.observed());
        assertEquals(3, first.collected());

        var unchanged = ExternalOrderLogic.creditNetworkOutput(10, 13,
                first.observed(), first.collected(), 8);
        assertEquals(first, unchanged);

        var second = ExternalOrderLogic.creditNetworkOutput(10, 20,
                unchanged.observed(), unchanged.collected(), 8);
        assertEquals(10, second.observed());
        assertEquals(8, second.collected());
    }
}
