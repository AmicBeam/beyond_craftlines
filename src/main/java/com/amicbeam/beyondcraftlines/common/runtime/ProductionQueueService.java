package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.structure.BlueprintLibrarySavedData;
import com.amicbeam.beyondcraftlines.common.structure.BlueprintRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

public final class ProductionQueueService
{
    private ProductionQueueService() {}

    public static ProductionJob enqueue(MinecraftServer server, UUID blueprintId, UUID owner,
                                        int networkId, int count)
    {
        if (count < 1 || networkId < 0) throw new IllegalArgumentException("invalid production request");
        BlueprintRecord record = BlueprintLibrarySavedData.get(server).get(blueprintId)
                .orElseThrow(() -> new IllegalArgumentException("blueprint not found"));
        if (record.compiled() == null || !record.owner().equals(owner))
            throw new IllegalArgumentException("blueprint is not compiled or owned by actor");
        ProductionJob job = new ProductionJob(UUID.randomUUID(), blueprintId, owner, networkId, count,
                ExecutorState.idle(), "");
        ProductionJobSavedData.get(server).put(job);
        return job;
    }

    public static void tick(MinecraftServer server)
    {
        ProductionJobSavedData data = ProductionJobSavedData.get(server);
        for (ProductionJob job : data.all()) tick(server, data, job);
    }

    private static void tick(MinecraftServer server, ProductionJobSavedData data, ProductionJob job)
    {
        ServerLevel level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (level == null) return;
        try
        {
            if (job.state().status() == ExecutorState.Status.IDLE)
            {
                Optional<ExecutorState> started = ExecutorService.begin(level, job.blueprintId(),
                        job.networkId(), level.getGameTime());
                if (started.isPresent()) data.put(job.withState(started.get()));
                return;
            }
            if (job.state().status() != ExecutorState.Status.RUNNING
                    && job.state().status() != ExecutorState.Status.PAUSED) return;
            ExecutorState next = ExecutorService.tick(level, job.blueprintId(), job.networkId(),
                    job.state(), level.getGameTime());
            if (next.status() == ExecutorState.Status.IDLE)
            {
                if (job.remaining() == 1) data.remove(job.id());
                else data.put(job.nextCycle());
            }
            else if (next != job.state()) data.put(job.withState(next));
        }
        catch (RuntimeException exception)
        {
            data.put(job.failed(exception.getMessage()));
        }
    }
}
