package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExecutorStatusFormatterTest
{
    @Test
    void formatsIdleWithoutTime()
    {
        assertEquals("IDLE", ExecutorStatusFormatter.format(ExecutorState.idle(), 40));
    }

    @Test
    void clampsFinishedRunningStateToZeroTicks()
    {
        ExecutorState state = new ExecutorState(ExecutorState.Status.RUNNING, 10, 20, "hash");
        assertEquals("RUNNING (0 ticks)", ExecutorStatusFormatter.format(state, -5));
    }

    @Test
    void formatsPausedStateWithRemainingTicks()
    {
        ExecutorState state = new ExecutorState(ExecutorState.Status.PAUSED, 10, 20, UUID.randomUUID().toString());
        assertEquals("PAUSED (7 ticks)", ExecutorStatusFormatter.format(state, 7));
    }
}
