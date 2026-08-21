package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.common.block.entity.NetFurnaceBlockEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

/** Loaded-furnace index for the BD 0.7.5 1.20.1 network furnace implementation. */
public final class NativeFurnaceRegistry {
    private static final Map<Location, Entry> LOADED = new HashMap<>();
    private static final Map<NetworkFamily, TreeSet<Location>> BY_NETWORK_FAMILY = new HashMap<>();
    private static final Comparator<Location> LOCATION_ORDER = Comparator
            .comparing((Location location) -> location.dimension().location().toString())
            .thenComparingLong(location -> location.position().asLong());

    private NativeFurnaceRegistry() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        chunk.getBlockEntities().values().forEach(NativeFurnaceRegistry::register);
    }
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        int x = event.getChunk().getPos().x, z = event.getChunk().getPos().z;
        java.util.List.copyOf(LOADED.keySet()).stream().filter(location -> location.dimension().equals(level.dimension())
                && (location.position().getX() >> 4) == x && (location.position().getZ() >> 4) == z).forEach(NativeFurnaceRegistry::remove);
    }
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) java.util.List.copyOf(LOADED.keySet()).stream()
                .filter(location -> location.dimension().equals(level.dimension())).forEach(NativeFurnaceRegistry::remove);
    }

    public static Optional<NativeFurnace> furnaceFor(MinecraftServer server, int networkId, String family) {
        TreeSet<Location> indexed = BY_NETWORK_FAMILY.get(new NetworkFamily(networkId, family));
        if (indexed == null) return Optional.empty();
        return java.util.List.copyOf(indexed).stream().map(LOADED::get).filter(java.util.Objects::nonNull)
                .map(entry -> validate(server, entry)).flatMap(Optional::stream).findFirst();
    }
    public static Set<String> availableFamilies(MinecraftServer server, int networkId) {
        HashSet<String> result = new HashSet<>();
        if (furnaceFor(server, networkId, "smelting").isPresent()) result.add("smelting");
        return Set.copyOf(result);
    }
    public static boolean supports(NetFurnaceBlockEntity furnace, String family) { return "smelting".equals(family); }

    private static void register(BlockEntity blockEntity) {
        if (!(blockEntity instanceof NetFurnaceBlockEntity furnace) || !(furnace.getLevel() instanceof ServerLevel level) || furnace.getNetId() < 0) return;
        Location location = new Location(level.dimension(), furnace.getBlockPos());
        remove(location);
        Entry entry = new Entry(level.dimension(), furnace.getBlockPos(), furnace.getNetId(), "smelting");
        LOADED.put(location, entry);
        BY_NETWORK_FAMILY.computeIfAbsent(new NetworkFamily(entry.networkId(), entry.family()), ignored -> new TreeSet<>(LOCATION_ORDER)).add(location);
    }
    private static Optional<NativeFurnace> validate(MinecraftServer server, Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null || !level.isLoaded(entry.position())) return Optional.empty();
        BlockEntity blockEntity = level.getBlockEntity(entry.position());
        if (!(blockEntity instanceof NetFurnaceBlockEntity furnace) || furnace.getNetId() != entry.networkId()) {
            remove(new Location(entry.dimension(), entry.position())); return Optional.empty();
        }
        return Optional.of(new NativeFurnace(level, furnace, entry.family()));
    }
    private static void remove(Location location) {
        Entry removed = LOADED.remove(location); if (removed == null) return;
        NetworkFamily key = new NetworkFamily(removed.networkId(), removed.family());
        TreeSet<Location> locations = BY_NETWORK_FAMILY.get(key);
        if (locations != null) { locations.remove(location); if (locations.isEmpty()) BY_NETWORK_FAMILY.remove(key); }
    }

    private record Location(ResourceKey<Level> dimension, BlockPos position) { private Location { position = position.immutable(); } }
    private record Entry(ResourceKey<Level> dimension, BlockPos position, int networkId, String family) { private Entry { position = position.immutable(); } }
    private record NetworkFamily(int networkId, String family) {}
    public record NativeFurnace(ServerLevel level, NetFurnaceBlockEntity blockEntity, String family) {}
}
