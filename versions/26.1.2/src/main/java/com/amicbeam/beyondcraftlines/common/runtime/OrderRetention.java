package com.amicbeam.beyondcraftlines.common.runtime;

/** Time-based visibility/lifetime policy for terminal orders. */
final class OrderRetention
{
    static final long COMPLETED_TICKS = 5L * 60L * 20L;

    private OrderRetention() {}

    static boolean expiredCompleted(boolean complete, long finishedAt, long now)
    {
        if (!complete) return false;
        if (finishedAt <= 0) return true; // Legacy completed records did not store a completion timestamp.
        return now >= finishedAt && now - finishedAt >= COMPLETED_TICKS;
    }
}
