package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.UUID;

public record ProductionJob(UUID id, UUID blueprintId, UUID owner, int networkId, int remaining,
                            ExecutorState state, String failure)
{
    public ProductionJob
    {
        if (id == null || blueprintId == null || owner == null || state == null)
            throw new IllegalArgumentException("production job fields are required");
        if (networkId < 0 || remaining < 1) throw new IllegalArgumentException("invalid production job");
        failure = failure == null ? "" : failure;
    }

    public ProductionJob withState(ExecutorState next)
    {
        return new ProductionJob(id, blueprintId, owner, networkId, remaining, next, failure);
    }

    public ProductionJob nextCycle()
    {
        return new ProductionJob(id, blueprintId, owner, networkId, remaining - 1,
                ExecutorState.idle(), failure);
    }

    public ProductionJob failed(String reason)
    {
        return new ProductionJob(id, blueprintId, owner, networkId, remaining,
                new ExecutorState(ExecutorState.Status.ERROR, state.startedAt(), state.finishAt(), state.blueprintHash()), reason);
    }
}
