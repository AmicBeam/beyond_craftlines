package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SandboxBoundaryTracker
{
    private static final int EXIT_DELAY_TICKS = 100;
    private final Map<UUID, Integer> outsideTicks = new HashMap<>();

    public boolean shouldExit(ServerPlayer player, SandboxSession session, StructureSnapshot snapshot)
    {
        if (!inside(player, session, snapshot))
        {
            int ticks = outsideTicks.merge(player.getUUID(), 1, Integer::sum);
            return ticks >= EXIT_DELAY_TICKS;
        }
        outsideTicks.remove(player.getUUID());
        return false;
    }

    public void remove(UUID player)
    {
        outsideTicks.remove(player);
    }

    private static boolean inside(ServerPlayer player, SandboxSession session, StructureSnapshot snapshot)
    {
        int padding = 2;
        return player.getX() >= session.slot().originX() - padding
                && player.getX() <= session.slot().maxX(snapshot.size().getX()) + padding + 1
                && player.getY() >= session.slot().originY() - padding
                && player.getY() <= session.slot().maxY(snapshot.size().getY()) + padding + 1
                && player.getZ() >= session.slot().originZ() - padding
                && player.getZ() <= session.slot().maxZ(snapshot.size().getZ()) + padding + 1;
    }
}
