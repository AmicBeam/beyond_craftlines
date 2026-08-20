package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.function.LongSupplier;

/** Cooperative node/deadline/interruption budget for off-thread client proposal search. */
final class ClientPlanningBudget
{
    private static final long DEFAULT_NANOS = 2_000_000_000L;
    private final int max;
    private final long deadline;
    private final LongSupplier nanoTime;
    private int used;

    ClientPlanningBudget(int max)
    { this(max, DEFAULT_NANOS, System::nanoTime); }

    ClientPlanningBudget(int max, long maxNanos, LongSupplier nanoTime)
    {
        if (max < 1 || maxNanos < 1 || nanoTime == null)
            throw new IllegalArgumentException("client planning budget must be positive");
        this.max = max;
        this.nanoTime = nanoTime;
        this.deadline = saturatingAdd(nanoTime.getAsLong(), maxNanos);
    }

    void enter()
    {
        if (Thread.currentThread().isInterrupted())
            throw new IllegalStateException("client planning cancelled");
        if (nanoTime.getAsLong() - deadline >= 0)
            throw new IllegalStateException("client planning time budget exhausted");
        if (++used > max) throw new IllegalStateException("client planning node budget exhausted");
    }

    int used() { return used; }

    private static long saturatingAdd(long left, long right)
    {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }
}
