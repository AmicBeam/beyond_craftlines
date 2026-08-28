package com.amicbeam.beyondcraftlines.common.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class CraftlineDashboardIndex
{
    private static final Map<MinecraftServer, Map<Integer, Set<Location>>> SERVERS = new WeakHashMap<>();
    private CraftlineDashboardIndex() {}

    public static synchronized void refresh(CraftlineDashboardBlockEntity dashboard)
    {
        if (!(dashboard.getLevel() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Location location = new Location(level.dimension(), dashboard.getBlockPos().immutable());
        Map<Integer, Set<Location>> networks = SERVERS.computeIfAbsent(server, ignored -> new HashMap<>());
        networks.values().forEach(entries -> entries.remove(location));
        networks.values().removeIf(Set::isEmpty);
        if (dashboard.isActiveDashboard())
            networks.computeIfAbsent(dashboard.getNetId(), ignored -> new HashSet<>()).add(location);
    }

    public static synchronized void remove(CraftlineDashboardBlockEntity dashboard)
    {
        if (!(dashboard.getLevel() instanceof ServerLevel level)) return;
        Map<Integer, Set<Location>> networks = SERVERS.get(level.getServer());
        if (networks == null) return;
        Location location = new Location(level.dimension(), dashboard.getBlockPos());
        networks.values().forEach(entries -> entries.remove(location));
        networks.values().removeIf(Set::isEmpty);
    }

    public static synchronized List<CraftlineDashboardBlockEntity> active(MinecraftServer server, int networkId)
    {
        Map<Integer, Set<Location>> networks = SERVERS.get(server);
        if (networks == null) return List.of();
        Set<Location> indexed = networks.get(networkId);
        if (indexed == null || indexed.isEmpty()) return List.of();
        List<CraftlineDashboardBlockEntity> result = new ArrayList<>();
        List<Location> stale = new ArrayList<>();
        for (Location location : List.copyOf(indexed))
        {
            ServerLevel level = server.getLevel(location.dimension());
            if (level == null || !level.isLoaded(location.position())
                    || !(level.getBlockEntity(location.position()) instanceof CraftlineDashboardBlockEntity dashboard)
                    || !dashboard.isActiveDashboard() || dashboard.getNetId() != networkId)
            { stale.add(location); continue; }
            result.add(dashboard);
        }
        indexed.removeAll(stale);
        result.sort(Comparator.comparing((CraftlineDashboardBlockEntity value) ->
                value.getLevel().dimension().toString()).thenComparingLong(value -> value.getBlockPos().asLong()));
        return List.copyOf(result);
    }

    private record Location(ResourceKey<Level> dimension, BlockPos position)
    { private Location { position = position.immutable(); } }
}
