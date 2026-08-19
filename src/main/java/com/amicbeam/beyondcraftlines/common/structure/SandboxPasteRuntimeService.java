package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class SandboxPasteRuntimeService
{
    private SandboxPasteRuntimeService() {}

    public static boolean tick(MinecraftServer server, UUID sessionId)
    {
        SandboxSession session = SandboxSessionSavedData.get(server).get(sessionId);
        if (session == null) return false;
        ServerLevel level = server.getLevel(SandboxDimension.KEY);
        if (level == null) return false;
        var record = BlueprintLibrarySavedData.get(server).get(session.blueprintId());
        if (record.isEmpty()) return false;
        var plan = SandboxPastePlanner.plan(record.get().snapshot(), session.slot());
        var data = SandboxPasteJobSavedData.get(server);
        SandboxPasteJob job = new SandboxPasteJob(plan, data.offset(sessionId));
        boolean complete = job.tick(level);
        data.put(sessionId, job.placementOffset());
        if (complete)
        {
            data.remove(sessionId);
            SandboxEndpointSavedData.get(server).put(sessionId,
                    SkyLogisticsEndpointDiscovery.find(level, session, record.get().snapshot()));
        }
        return complete;
    }
}
