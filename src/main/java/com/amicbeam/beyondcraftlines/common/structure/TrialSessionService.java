package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class TrialSessionService
{
    private TrialSessionService() {}

    public static TrialSession create(MinecraftServer server, UUID blueprintId, UUID owner)
    {
        TrialSession existing = TrialSessionSavedData.get(server).get(blueprintId);
        if (existing != null && existing.state().status() != TrialRunState.Status.FAILED)
            throw new IllegalStateException("trial session already exists");
        TrialSession session = TrialSession.create(blueprintId, owner);
        TrialSessionSavedData.get(server).put(session);
        return session;
    }

    public static TrialSession start(MinecraftServer server, UUID blueprintId, UUID owner,
                                     long now, long duration)
    {
        TrialSessionSavedData data = TrialSessionSavedData.get(server);
        TrialSession session = requireOwner(data.get(blueprintId), owner);
        TrialSession started = session.start(now, duration);
        data.put(started);
        return started;
    }

    public static TrialSession complete(MinecraftServer server, UUID blueprintId, UUID owner,
                                        long now, TrialObservation observation)
    {
        TrialSessionSavedData data = TrialSessionSavedData.get(server);
        TrialSession session = requireOwner(data.get(blueprintId), owner);
        TrialSession completed = session.complete(now, observation);
        data.put(completed);
        return completed;
    }

    public static TrialSession fail(MinecraftServer server, UUID blueprintId, UUID owner, String reason)
    {
        TrialSessionSavedData data = TrialSessionSavedData.get(server);
        TrialSession session = requireOwner(data.get(blueprintId), owner);
        TrialSession failed = session.fail(reason);
        data.put(failed);
        return failed;
    }

    public static TrialSession cancel(MinecraftServer server, UUID blueprintId, UUID owner)
    {
        TrialSessionSavedData data = TrialSessionSavedData.get(server);
        TrialSession session = requireOwner(data.get(blueprintId), owner);
        data.remove(blueprintId);
        return session;
    }

    private static TrialSession requireOwner(TrialSession session, UUID owner)
    {
        if (session == null || !session.owner().equals(owner))
            throw new IllegalArgumentException("trial session not found or not owned by actor");
        return session;
    }
}
