package com.amicbeam.beyondcraftlines.common.crafting;

/** Hard guard for candidate-branch optimization; fixed proposals do not consume this budget. */
final class PlanningBudget
{
    private final int maxNodes;
    private final long startedNanos;
    private final long maxNanos;
    private int nodes;

    PlanningBudget(int maxNodes, long maxNanos)
    {
        if (maxNodes < 1 || maxNanos < 1) throw new IllegalArgumentException("invalid planning budget");
        this.maxNodes = maxNodes;
        this.startedNanos = System.nanoTime();
        this.maxNanos = maxNanos;
    }

    void enterBranch()
    {
        if (++nodes > maxNodes) exceeded();
        checkTime();
    }

    void checkTime()
    {
        if (System.nanoTime() - startedNanos > maxNanos) exceeded();
    }

    void checkGeneratedVariants(int variants)
    {
        if (variants > maxNodes) exceeded();
        checkTime();
    }

    private static void exceeded()
    {
            throw new IllegalStateException("recipe tree is too complex; planning budget exceeded");
    }

    int nodes() { return nodes; }
}
