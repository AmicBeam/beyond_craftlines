package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

public final class SandboxExitService
{
    private SandboxExitService() {}

    public static void enter(MinecraftServer server, ServerPlayer player, SandboxSession session)
    {
        SandboxPlayerStateSavedData.get(server).put(SandboxPlayerState.capture(player, session.id()));
        ServerLevel level = server.getLevel(SandboxDimension.KEY);
        if (level == null) throw new IllegalStateException("sandbox dimension is not loaded");
        player.teleportTo(level, session.slot().originX() + 0.5, session.slot().originY() + 2.0,
                session.slot().originZ() + 0.5, Set.of(), player.getYRot(), player.getXRot());
        player.setGameMode(GameType.SPECTATOR);
    }

    public static boolean exit(MinecraftServer server, ServerPlayer player, UUID sessionId)
    {
        SandboxPlayerState state = SandboxPlayerStateSavedData.get(server).get(player.getUUID());
        if (state == null || !state.session().equals(sessionId)) return false;
        net.minecraft.resources.ResourceLocation location =
                net.minecraft.resources.ResourceLocation.parse(state.dimension());
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, location);
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return false;
        player.teleportTo(level, state.x(), state.y(), state.z(), Set.of(), state.yaw(), state.pitch());
        player.setGameMode(state.gameType());
        SandboxSession session = SandboxSessionSavedData.get(server).get(sessionId);
        if (session != null)
        {
            BlueprintLibrarySavedData.get(server).get(session.blueprintId()).ifPresent(record ->
            {
                ServerLevel sandbox = server.getLevel(SandboxDimension.KEY);
                if (sandbox != null)
                    SandboxCleanupExecutor.clear(sandbox,
                            SandboxPastePlanner.plan(record.snapshot(), session.slot()));
            });
            SandboxEndpointSavedData.get(server).remove(sessionId);
        }
        SandboxPlayerStateSavedData.get(server).remove(player.getUUID());
        SandboxManager.release(server, sessionId, player.getUUID());
        return true;
    }
}
