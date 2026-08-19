package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SandboxRuntimeService
{
    private static final SandboxBoundaryTracker BOUNDARIES = new SandboxBoundaryTracker();

    private SandboxRuntimeService() {}

    public static void tickPlayer(ServerPlayer player)
    {
        MinecraftServer server = player.getServer();
        if (server == null || !player.level().dimension().equals(SandboxDimension.KEY)) return;
        SandboxPlayerState state = SandboxPlayerStateSavedData.get(server).get(player.getUUID());
        if (state == null) return;
        SandboxSession session = SandboxSessionSavedData.get(server).get(state.session());
        if (session == null) return;
        BlueprintLibrarySavedData.get(server).get(session.blueprintId()).ifPresent(record -> {
            if (BOUNDARIES.shouldExit(player, session, record.snapshot()))
            {
                SandboxExitService.exit(server, player, session.id());
                BOUNDARIES.remove(player.getUUID());
            }
        });
    }
}
