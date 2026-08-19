package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class TrialMeasurementService
{
    private TrialMeasurementService() {}

    public static void begin(MinecraftServer server, UUID blueprintId, int networkId)
    {
        if (blueprintId == null) throw new IllegalArgumentException("blueprint ID is required");
        TrialMeasurementSavedData data = TrialMeasurementSavedData.get(server);
        data.put(blueprintId, new TrialMeasurementAccumulator());
        data.putSnapshot(blueprintId, TrialNetworkSnapshot.capture(networkId));
    }

    public static void begin(MinecraftServer server, UUID blueprintId)
    {
        if (blueprintId == null) throw new IllegalArgumentException("blueprint ID is required");
        TrialMeasurementSavedData.get(server).put(blueprintId, new TrialMeasurementAccumulator());
    }

    public static TrialMeasurementAccumulator get(MinecraftServer server, UUID blueprintId)
    {
        return TrialMeasurementSavedData.get(server).get(blueprintId);
    }

    public static TrialObservation finish(MinecraftServer server, UUID blueprintId, UUID owner, long now)
    {
        TrialMeasurementSavedData data = TrialMeasurementSavedData.get(server);
        TrialMeasurementAccumulator accumulator = data.get(blueprintId);
        if (accumulator == null) throw new IllegalStateException("trial measurement is not active");
        TrialNetworkSnapshot before = data.snapshot(blueprintId);
        if (before != null)
        {
            throw new IllegalStateException("trial network measurement requires a network-aware finish call");
        }
        TrialObservation observation = accumulator.build();
        TrialSessionService.complete(server, blueprintId, owner, now, observation);
        data.remove(blueprintId);
        TrialReportDeliveryService.deliver(server, blueprintId, owner, observation);
        return observation;
    }

    public static TrialObservation finish(MinecraftServer server, UUID blueprintId, UUID owner,
                                           long now, int networkId)
    {
        TrialMeasurementSavedData data = TrialMeasurementSavedData.get(server);
        TrialMeasurementAccumulator accumulator = data.get(blueprintId);
        TrialNetworkSnapshot before = data.snapshot(blueprintId);
        if (accumulator == null || before == null)
            throw new IllegalStateException("trial network measurement is not active");
        if (before.networkId() != networkId)
            throw new IllegalStateException("trial must finish on its starting network");
        TrialNetworkSnapshot after;
        try
        {
            after = TrialNetworkSnapshot.capture(networkId);
        }
        catch (IllegalStateException unavailable)
        {
            throw new IllegalStateException("trial network is temporarily unavailable", unavailable);
        }
        TrialNetworkMeasurement.apply(accumulator, before, after);
        TrialSession session = TrialSessionSavedData.get(server).get(blueprintId);
        if (session == null || session.state().status() != TrialRunState.Status.RUNNING)
            throw new IllegalStateException("trial session is not running");
        accumulator.setCycleTicks(Math.max(1L, now - session.state().startedAt()));
        TrialObservation observation = accumulator.build();
        TrialSessionService.complete(server, blueprintId, owner, now, observation);
        data.remove(blueprintId);
        TrialReportDeliveryService.deliver(server, blueprintId, owner, observation);
        return observation;
    }

    public static void discard(MinecraftServer server, UUID blueprintId)
    {
        TrialMeasurementSavedData.get(server).remove(blueprintId);
    }
}
