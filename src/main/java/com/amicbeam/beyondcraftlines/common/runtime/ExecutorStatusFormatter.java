package com.amicbeam.beyondcraftlines.common.runtime;

public final class ExecutorStatusFormatter
{
    private ExecutorStatusFormatter() {}

    public static String format(ExecutorState state, long remainingTicks)
    {
        if (state.status() == ExecutorState.Status.RUNNING
                || state.status() == ExecutorState.Status.PAUSED)
        {
            return state.status().name() + " (" + Math.max(0, remainingTicks) + " ticks)";
        }
        return state.status().name();
    }
}
