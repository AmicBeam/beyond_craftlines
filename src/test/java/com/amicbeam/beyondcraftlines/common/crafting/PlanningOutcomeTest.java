package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlanningOutcomeTest
{
    @Test void unfinishedAlternativeSearchDoesNotClaimThatEveryRecipeIsCyclic()
    {
        assertEquals(PlanningOutcome.BUDGET_EXHAUSTED,
                PlanningOutcome.completed(true, false, true, true));
    }

    @Test void missingRawMaterialsArePreferredToACompressionLoop()
    {
        assertTrue(PlanningOutcome.prefersAlternative(
                PlanningOutcome.CYCLE, 1, PlanningOutcome.MISSING_INPUTS, 9));
        assertFalse(PlanningOutcome.prefersAlternative(
                PlanningOutcome.MISSING_INPUTS, 9, PlanningOutcome.CYCLE, 1));
        assertTrue(PlanningOutcome.prefersAlternative(
                PlanningOutcome.MISSING_INPUTS, 9, PlanningOutcome.READY, 0));
    }

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
