package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

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
}
