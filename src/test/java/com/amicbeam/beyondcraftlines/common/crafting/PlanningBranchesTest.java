package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
