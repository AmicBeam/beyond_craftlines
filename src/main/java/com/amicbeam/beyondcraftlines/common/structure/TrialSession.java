package com.amicbeam.beyondcraftlines.common.structure;

import java.util.UUID;

public record TrialSession(UUID blueprintId, UUID owner, TrialRunState state, TrialObservation observation)
{
    public TrialSession {
        if (blueprintId == null || owner == null || state == null)
            throw new IllegalArgumentException("trial session identity and state are required");
    }

    public static TrialSession create(UUID blueprintId, UUID owner)
    {
        return new TrialSession(blueprintId, owner, TrialRunState.ready(), null);
    }

    public TrialSession start(long now, long duration)
    {
        if (state.status() != TrialRunState.Status.READY)
            throw new IllegalStateException("trial is not ready");
        return new TrialSession(blueprintId, owner, TrialRunState.start(now, duration), observation);
    }

    public TrialSession complete(long now, TrialObservation result)
    {
        if (state.status() != TrialRunState.Status.RUNNING)
            throw new IllegalStateException("trial is not running");
        if (result == null) throw new IllegalArgumentException("trial observation is required");
        TrialRunState finished = state.finish(now);
        if (finished.status() != TrialRunState.Status.COMPLETE)
            throw new IllegalStateException("trial duration has not elapsed");
        return new TrialSession(blueprintId, owner, finished, result);
    }

    public TrialSession fail(String reason)
    {
        return new TrialSession(blueprintId, owner, state.fail(reason), observation);
    }
}
