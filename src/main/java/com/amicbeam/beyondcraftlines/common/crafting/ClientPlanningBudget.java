package com.amicbeam.beyondcraftlines.common.crafting;

/** Pure cooperative node budget for off-thread client proposal search. */
final class ClientPlanningBudget
{
    private final int max;
    private int used;

    ClientPlanningBudget(int max)
    {
        if (max < 1) throw new IllegalArgumentException("client planning budget must be positive");
        this.max = max;
    }

    void enter()
    {
        if (++used > max) throw new IllegalStateException("client planning node budget exhausted");
    }

    int used() { return used; }
}
