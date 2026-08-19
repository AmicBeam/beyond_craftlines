package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.MinecraftServer;

public final class TrialSessionTickService
{
    private TrialSessionTickService() {}

    public static void tick(MinecraftServer server)
    {
        TrialSessionSavedData data = TrialSessionSavedData.get(server);
        long now = server.overworld().getGameTime();
        for (TrialSession session : data.all())
        {
            if (session.state().status() != TrialRunState.Status.RUNNING || now < session.state().finishAt()) continue;
            TrialNetworkSnapshot snapshot = TrialMeasurementSavedData.get(server)
                    .snapshot(session.blueprintId());
            if (snapshot != null)
            {
                try
                {
                    TrialMeasurementService.finish(server, session.blueprintId(), session.owner(),
                            now, snapshot.networkId());
                }
                catch (IllegalStateException ignored)
                {
                    // Keep the running session until its network becomes available again.
                }
                continue;
            }
            data.put(new TrialSession(session.blueprintId(), session.owner(),
                    session.state().finish(now), session.observation()));
        }
    }
}
