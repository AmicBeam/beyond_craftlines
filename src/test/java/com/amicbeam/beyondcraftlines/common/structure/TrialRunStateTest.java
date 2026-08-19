package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrialRunStateTest
{
    @Test
    void completesOnlyAfterDuration()
    {
        TrialRunState running = TrialRunState.start(100, 20);
        assertEquals(TrialRunState.Status.RUNNING, running.finish(119).status());
        assertEquals(TrialRunState.Status.COMPLETE, running.finish(120).status());
    }

    @Test
    void recordsFailureReason()
    {
        TrialRunState failed = TrialRunState.start(0, 1).fail("machine stopped");
        assertEquals(TrialRunState.Status.FAILED, failed.status());
        assertEquals("machine stopped", failed.failureReason());
    }

    @Test
    void rejectsInvalidStartAndFailure()
    {
        assertThrows(IllegalArgumentException.class, () -> TrialRunState.start(0, 0));
        assertThrows(IllegalArgumentException.class, () -> TrialRunState.start(0, 1).fail(""));
    }
}
