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
    private final boolean optimalSearch;
    private final Set<String> visited = new HashSet<>();
    private int used;
    private boolean exhausted;

    ClientPlanningBudget(int max)
    { this(max, ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS, System::nanoTime, true); }

    ClientPlanningBudget(int max, long maxNanos, LongSupplier nanoTime)
    { this(max, maxNanos, nanoTime, true); }

    ClientPlanningBudget(int max, long maxNanos, LongSupplier nanoTime, boolean optimalSearch)
    {
        if (max < 1 || maxNanos < 1 || nanoTime == null)
            throw new IllegalArgumentException("client planning budget must be positive");
        this.max = max;
        this.nanoTime = nanoTime;
        this.optimalSearch = optimalSearch;
        this.deadline = saturatingAdd(nanoTime.getAsLong(), maxNanos);
    }

    boolean visit(String identity)
    {
        checkCancellation();
        if (nanoTime.getAsLong() - deadline >= 0)
        {
            exhausted = true;
            return false;
        }
        if (visited.contains(identity)) return true;
        if (used >= max) { exhausted = true; return false; }
        visited.add(identity);
        used++;
        return true;
    }

    boolean canOptimize()
    { return optimalSearch && canSearch(); }

    boolean canSearch()
    {
        checkCancellation();
        if (nanoTime.getAsLong() - deadline >= 0) exhausted = true;
        return !exhausted;
    }

    int used() { return used; }

    boolean exhausted()
    {
        canSearch();
        return exhausted;
    }

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
