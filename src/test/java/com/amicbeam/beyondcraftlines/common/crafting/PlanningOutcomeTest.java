package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlanningOutcomeTest
{
    @Test void terminalCausesDoNotCollapseIntoMissingInputs()
    {
        assertEquals(PlanningOutcome.NO_RECIPE, PlanningOutcome.completed(true, true, false, false));
        assertEquals(PlanningOutcome.CYCLE, PlanningOutcome.completed(true, false, true, false));
        assertEquals(PlanningOutcome.BUDGET_EXHAUSTED,
                PlanningOutcome.completed(true, false, false, true));
        assertEquals(PlanningOutcome.MISSING_INPUTS,
                PlanningOutcome.completed(true, false, false, false));
        assertEquals(PlanningOutcome.READY, PlanningOutcome.completed(false, true, true, true));
    }
}
