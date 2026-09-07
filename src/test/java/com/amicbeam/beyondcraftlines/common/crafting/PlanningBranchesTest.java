package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningBranchesTest
{
    @Test
    void fixedRecipeAndIngredientChoicesUseTheDirectPath()
    {
        assertFalse(PlanningBranches.recipesRequireBranches(1));
        assertFalse(PlanningBranches.ingredientsRequireBranches(List.of(List.of("iron"), List.of("hammer"))));
    }

    @Test
    void alternativesStillRequireIsolation()
    {
        assertTrue(PlanningBranches.recipesRequireBranches(2));
        assertTrue(PlanningBranches.ingredientsRequireBranches(List.of(List.of("iron", "copper"))));
    }

    @Test
    void expiredSearchStopsBeforeAnotherCandidate()
    {
        AtomicLong now = new AtomicLong();
        ClientPlanningBudget budget = new ClientPlanningBudget(10, 5, now::get);
        now.set(5);
        assertFalse(PlanningBranches.shouldTryCandidate(false, budget));
        assertFalse(PlanningBranches.shouldTryCandidate(true, budget));
    }

    @Test
    void lightweightSearchOnlyTriesTheFirstCandidate()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(10, 5, () -> 0, false);
        assertTrue(PlanningBranches.shouldTryCandidate(false, budget));
        assertFalse(PlanningBranches.shouldTryCandidate(true, budget));
    }
}
