package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExecutorServiceTest
{
    @Test
    void idleStateHasNoRuntimeWindow()
    {
        ExecutorState state = ExecutorState.idle();

        assertEquals(ExecutorState.Status.IDLE, state.status());
        assertEquals(0, state.startedAt());
        assertEquals(0, state.finishAt());
    }

    @Test
    void runningStatePreservesItsWindow()
    {
        ExecutorState state = new ExecutorState(
                ExecutorState.Status.RUNNING, 100, 200, "hash");

        assertEquals(100, state.startedAt());
        assertEquals(200, state.finishAt());
        assertEquals("hash", state.blueprintHash());
    }
}
