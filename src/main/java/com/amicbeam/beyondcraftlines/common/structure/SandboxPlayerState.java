package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

public record SandboxPlayerState(
        UUID player,
        UUID session,
        String dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        GameType gameType)
{
    public SandboxPlayerState
    {
        if (player == null || session == null || dimension == null || gameType == null)
            throw new IllegalArgumentException("sandbox player state fields are required");
    }

    public static SandboxPlayerState capture(ServerPlayer player, UUID session)
    {
        return new SandboxPlayerState(player.getUUID(), session,
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
    }
}
