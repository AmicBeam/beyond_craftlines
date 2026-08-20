package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientPlanningBudgetTest
{
    @Test
    void permitsConfiguredNodesAndRejectsTheNext()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(2);
        budget.enter();
        budget.enter();
        assertEquals(2, budget.used());
        assertThrows(IllegalStateException.class, budget::enter);
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
        assertThrows(IllegalStateException.class, budget::enter);
    }

    @Test
    void cooperatesWithTaskCancellation()
    {
        ClientPlanningBudget budget = new ClientPlanningBudget(10);
        Thread.currentThread().interrupt();
        try { assertThrows(IllegalStateException.class, budget::enter); }
        finally { Thread.interrupted(); }
    }
}
