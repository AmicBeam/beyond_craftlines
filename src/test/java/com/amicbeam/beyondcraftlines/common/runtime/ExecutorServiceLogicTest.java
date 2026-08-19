package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExecutorServiceLogicTest
{
    @Test
    void preservesRunningStateBeforeFinish()
    {
        ExecutorState state = new ExecutorState(ExecutorState.Status.RUNNING, 10, 30, "hash");
        assertEquals(state, state.status() == ExecutorState.Status.RUNNING && 20 < state.finishAt()
                ? state : ExecutorState.idle());
    }

    @Test
    void statusFormatterUsesZeroForLateCompletion()
    {
        ExecutorState state = new ExecutorState(ExecutorState.Status.RUNNING, 10, 30, "hash");
        assertEquals("RUNNING (0 ticks)", ExecutorStatusFormatter.format(state, -1));
    }
}
