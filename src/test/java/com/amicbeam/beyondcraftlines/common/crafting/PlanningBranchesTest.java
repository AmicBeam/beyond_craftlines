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
    void lightweightSearchStopsOptimizingAfterFindingAUsableCandidate()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(10, 5, () -> 0, false);
        assertTrue(PlanningBranches.shouldTryCandidate(false, budget));
        assertFalse(PlanningBranches.shouldTryCandidate(true, budget));
    }

    @Test
    void lightweightSearchCanRejectALoopAndTryAStockedAlternative()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(10, 5, () -> 0, false);
        List<String> evaluated = new java.util.ArrayList<>();
        boolean viable = false;
        for (String candidate : List.of("cyclic", "missing", "stocked", "optional_optimization"))
        {
            if (!PlanningBranches.shouldTryCandidate(viable, budget)) break;
            var result = PlanningCycleBranch.evaluateWithStatus("missing", () -> {
                if (candidate.equals("cyclic")) throw new PlanningCycleBranch.Cycle();
                return candidate;
            }, ignored -> "missing");
            evaluated.add(candidate);
            viable = !result.cyclic() && result.state().equals("stocked");
        }
        org.junit.jupiter.api.Assertions.assertEquals(List.of("cyclic", "missing", "stocked"), evaluated);
    }
}
