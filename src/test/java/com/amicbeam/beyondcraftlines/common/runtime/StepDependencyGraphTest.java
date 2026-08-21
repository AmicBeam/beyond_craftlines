package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StepDependencyGraphTest
{
    @Test
    void independentSiblingsAreReadyTogether()
    {
        List<Boolean> complete = List.of(false, false, false);
        assertTrue(StepDependencyGraph.ready(0, 3, List.of(), complete::get));
        assertTrue(StepDependencyGraph.ready(1, 3, List.of(), complete::get));
        assertFalse(StepDependencyGraph.ready(2, 3, List.of(0, 1), complete::get));
    }

    @Test
    void parentBecomesReadyAfterEveryDependencyCompletes()
    {
        assertFalse(StepDependencyGraph.ready(2, 3, List.of(0, 1), List.of(true, false, false)::get));
        assertTrue(StepDependencyGraph.ready(2, 3, List.of(0, 1), List.of(true, true, false)::get));
    }

    @Test
    void rejectsForwardAndOutOfRangeDependencies()
    {
        assertFalse(StepDependencyGraph.ready(1, 3, List.of(1), ignored -> true));
        assertFalse(StepDependencyGraph.ready(1, 3, List.of(3), ignored -> true));
    }
}
