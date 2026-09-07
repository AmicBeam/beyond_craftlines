package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPlanningBudgetTest
{
    @Test
    void permitsConfiguredOptimizationNodesAndStopsTheNext()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(2);
        budget.visit("minecraft:coal");
        budget.visit("minecraft:iron_ingot");
        assertEquals(2, budget.used());
        budget.visit("minecraft:redstone");
        assertFalse(budget.canOptimize());
        assertTrue(budget.exhausted());
    }

    @Test
    void repeatedItemIdentityConsumesBudgetOnce()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(1);
        budget.visit("minecraft:coal");
        budget.visit("minecraft:coal");
        assertEquals(1, budget.used());
        org.junit.jupiter.api.Assertions.assertTrue(budget.canOptimize());
    }

    @Test
    void rejectsNonPositiveLimit()
    {
        assertThrows(IllegalArgumentException.class, () -> new ClientPlanningBudget(0));
    }

    @Test
    void rejectsWorkAtTheDeadline()
    {
        AtomicLong now = new AtomicLong();
        ClientPlanningBudget budget = new ClientPlanningBudget(10, 5, now::get);
        now.set(5);
        budget.visit("minecraft:coal");
        assertFalse(budget.canOptimize());
        assertTrue(budget.exhausted());
    }

    @Test
    void cooperatesWithTaskCancellation()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(10);
        Thread.currentThread().interrupt();
        try { assertThrows(IllegalStateException.class, budget::canOptimize); }
        finally { Thread.interrupted(); }
    }
}
