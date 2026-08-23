package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.function.LongSupplier;
import java.util.HashSet;
import java.util.Set;

/** Candidate-search budget plus hard cancellation for off-thread client proposal search. */
final class ClientPlanningBudget
{
    private final int max;
    private final long deadline;
    private final LongSupplier nanoTime;
    private final Set<String> visited = new HashSet<>();
    private int used;
    private boolean exhausted;

    ClientPlanningBudget(int max)
    { this(max, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, System::nanoTime); }

    ClientPlanningBudget(int max, long maxNanos, LongSupplier nanoTime)
    {
        if (max < 1 || maxNanos < 1 || nanoTime == null)
            throw new IllegalArgumentException("client planning budget must be positive");
        this.max = max;
        this.nanoTime = nanoTime;
        this.deadline = saturatingAdd(nanoTime.getAsLong(), maxNanos);
    }

    void visit(String identity)
    {
        checkCancellation();
        if (visited.contains(identity)) return;
        if (nanoTime.getAsLong() - deadline >= 0 || used >= max)
        {
            exhausted = true;
            return;
        }
        visited.add(identity);
        used++;
    }

    boolean canOptimize()
    {
        checkCancellation();
        if (nanoTime.getAsLong() - deadline >= 0) exhausted = true;
        return !exhausted;
    }

    int used() { return used; }

    void checkCancellation()
    {
        if (Thread.currentThread().isInterrupted())
            throw new IllegalStateException("client planning cancelled");
    }

    private static long saturatingAdd(long left, long right)
    {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }
}
