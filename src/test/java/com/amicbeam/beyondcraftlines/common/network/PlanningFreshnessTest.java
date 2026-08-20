package com.amicbeam.beyondcraftlines.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningFreshnessTest
{
    @Test
    void unrelatedInventoryRevisionDoesNotInvalidateAProposal()
    {
        assertEquals(PlanningFreshness.Result.VALID,
                PlanningFreshness.evaluate(10, 11, 7, 7, true));
    }

    @Test
    void recipeEpochChangeStillInvalidatesAProposal()
    {
        assertFalse(PlanningFreshness.recipesChanged(7, 7));
        assertTrue(PlanningFreshness.recipesChanged(7, 8));
        assertEquals(PlanningFreshness.Result.RECIPES_CHANGED,
                PlanningFreshness.evaluate(10, 10, 7, 8, true));
    }

    @Test
    void relevantMaterialShortageInvalidatesAfterReplan()
    { assertEquals(PlanningFreshness.Result.REQUIRED_MATERIALS_CHANGED,
            PlanningFreshness.evaluate(10, 11, 7, 7, false)); }
}
