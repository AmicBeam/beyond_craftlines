package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExecutorStateTest
{
    @Test
    void idleStateStartsWithoutRuntimeWindow()
    {
        ExecutorState state = ExecutorState.idle();

        assertEquals(ExecutorState.Status.IDLE, state.status());
        assertEquals(0, state.startedAt());
        assertEquals(0, state.finishAt());
        assertEquals("", state.blueprintHash());
    }

    @Test
    void preservesRunningWindowAndHash()
    {
        ExecutorState state = new ExecutorState(
                ExecutorState.Status.RUNNING, 120, 320, "structure-hash");

        assertEquals(ExecutorState.Status.RUNNING, state.status());
        assertEquals(120, state.startedAt());
        assertEquals(320, state.finishAt());
        assertEquals("structure-hash", state.blueprintHash());
    }
}
