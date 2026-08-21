package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PlanningBudgetTest
{
    @Test
    void rejectsOptimizationBeyondTheBranchLimit()
    {
        PlanningBudget budget = new PlanningBudget(3, Long.MAX_VALUE);
        budget.enterBranch();
        budget.enterBranch();
        budget.enterBranch();
        assertEquals(3, budget.nodes());
        assertThrows(IllegalStateException.class, budget::enterBranch);
    }
}
