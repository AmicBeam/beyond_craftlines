package com.amicbeam.beyondcraftlines.common.runtime;

/** Time-based visibility/lifetime policy for terminal orders. */
final class OrderRetention
{
    static final long DISPLAY_TICKS = 5L * 60L * 20L;

    private OrderRetention() {}

    static boolean expired(RecipeOrderJob.Status status, long finishedAt, long now)
    {
        if (status != RecipeOrderJob.Status.COMPLETE && status != RecipeOrderJob.Status.CANCELLED) return false;
        if (finishedAt <= 0) return true; // Legacy terminal records may not store a finish timestamp.
        return now >= finishedAt && now - finishedAt >= DISPLAY_TICKS;
    }
}
