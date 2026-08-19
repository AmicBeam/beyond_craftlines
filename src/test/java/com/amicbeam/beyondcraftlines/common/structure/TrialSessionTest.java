package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrialSessionTest
{
    @Test
    void runsAndCompletesWithObservation()
    {
        TrialSession session = TrialSession.create(UUID.randomUUID(), UUID.randomUUID()).start(10, 5);
        TrialObservation observation = new TrialObservation(List.of(), List.of(), 10, 5);
        TrialSession completed = session.complete(15, observation);

        assertEquals(TrialRunState.Status.COMPLETE, completed.state().status());
        assertEquals(observation, completed.observation());
    }

    @Test
    void rejectsCompletionBeforeDeadline()
    {
        TrialSession session = TrialSession.create(UUID.randomUUID(), UUID.randomUUID()).start(10, 5);
        TrialObservation observation = new TrialObservation(List.of(), List.of(), 10, 5);
        assertThrows(IllegalStateException.class, () -> session.complete(14, observation));
    }

    @Test
    void recordsFailure()
    {
        TrialSession session = TrialSession.create(UUID.randomUUID(), UUID.randomUUID()).fail("sandbox unavailable");
        assertEquals(TrialRunState.Status.FAILED, session.state().status());
        assertEquals("sandbox unavailable", session.state().failureReason());
    }
}
