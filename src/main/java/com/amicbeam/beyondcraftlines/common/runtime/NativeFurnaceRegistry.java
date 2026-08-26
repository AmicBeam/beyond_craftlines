package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.event.dimensionnet.NetedBlockEvent;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;

/** Runtime index of loaded BD network furnaces. No persistent copy is needed. */
public final class NativeFurnaceRegistry
{
    private static final Map<Location, Entry> LOADED = new HashMap<>();
    private static final Map<NetworkFamily, TreeSet<Location>> BY_NETWORK_FAMILY = new HashMap<>();
    private static final DeferredRegistrationQueue<Location> DEFERRED = new DeferredRegistrationQueue<>();
    private static final Comparator<Location> LOCATION_ORDER = Comparator
            .comparing((Location location) -> location.dimension().location().toString())
            .thenComparingLong(location -> location.position().asLong());

    private NativeFurnaceRegistry() {}

    public static void onBound(NetedBlockEvent.Bound event)
    {
        register(event.getBlockEntity());
    }

    public static void onUnbound(NetedBlockEvent.Unbound event)
    {
        remove(new Location(event.getLevel().dimension(), event.getPos()));
    }

    public static void onBlockPlaced(ServerLevel level, BlockPos position)
    {
        if (level.getBlockEntity(position) instanceof BaseNetFurnaceBlockEntity<?>)
            DEFERRED.schedule(new Location(level.dimension(), position));
    }

    public static void tick(MinecraftServer server)
    {
        for (Location location : DEFERRED.drain())
        {
            ServerLevel level = server.getLevel(location.dimension());
            if (level != null && level.isLoaded(location.position()))
                register(level.getBlockEntity(location.position()));
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event)
    {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        chunk.getBlockEntities().values().forEach(NativeFurnaceRegistry::register);
    }

    public static void onChunkUnload(ChunkEvent.Unload event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;
        java.util.List.copyOf(LOADED.keySet()).stream()
                .filter(location -> location.dimension().equals(level.dimension())
                && (location.position().getX() >> 4) == chunkX
                && (location.position().getZ() >> 4) == chunkZ).forEach(NativeFurnaceRegistry::remove);
    }

    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel level)
            java.util.List.copyOf(LOADED.keySet()).stream()
                    .filter(location -> location.dimension().equals(level.dimension()))
                    .forEach(NativeFurnaceRegistry::remove);
    }

    public static Optional<NativeFurnace> furnaceFor(MinecraftServer server, int networkId, String family)
    {
        TreeSet<Location> indexed = BY_NETWORK_FAMILY.get(new NetworkFamily(networkId, family));
        if (indexed == null) return Optional.empty();
        return java.util.List.copyOf(indexed).stream()
                .map(LOADED::get).filter(java.util.Objects::nonNull)
                .map(entry -> validate(server, entry))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static Set<String> availableFamilies(MinecraftServer server, int networkId)
    {
        HashSet<String> result = new HashSet<>();
        for (String family : Set.of("smelting", "blasting", "smoking"))
            if (furnaceFor(server, networkId, family).isPresent()) result.add(family);
        return Set.copyOf(result);
    }

    public static boolean supports(BaseNetFurnaceBlockEntity<?> furnace, String family)
    {
        return family.equals(family(furnace));
    }

    private static void register(BlockEntity blockEntity)
    {
        if (!(blockEntity instanceof BaseNetFurnaceBlockEntity<?> furnace)
                || !(furnace.getLevel() instanceof ServerLevel level) || furnace.getNetId() < 0) return;
        String family = family(furnace);
        if (family == null) return;
        Location location = new Location(level.dimension(), furnace.getBlockPos());
        remove(location);
        Entry entry = new Entry(level.dimension(), furnace.getBlockPos(), furnace.getNetId(), family);
        LOADED.put(location, entry);
        BY_NETWORK_FAMILY.computeIfAbsent(new NetworkFamily(entry.networkId(), entry.family()),
                ignored -> new TreeSet<>(LOCATION_ORDER)).add(location);
    }

    private static Optional<NativeFurnace> validate(MinecraftServer server, Entry entry)
    {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null || !level.isLoaded(entry.position())) return Optional.empty();
        BlockEntity blockEntity = level.getBlockEntity(entry.position());
        if (!(blockEntity instanceof BaseNetFurnaceBlockEntity<?> furnace)
                || furnace.getNetId() != entry.networkId() || !entry.family().equals(family(furnace)))
        {
            remove(new Location(entry.dimension(), entry.position()));
            return Optional.empty();
        }
        return Optional.of(new NativeFurnace(level, furnace, entry.family()));
    }

    private static void remove(Location location)
    {
        Entry removed = LOADED.remove(location);
        if (removed == null) return;
        NetworkFamily key = new NetworkFamily(removed.networkId(), removed.family());
        TreeSet<Location> locations = BY_NETWORK_FAMILY.get(key);
        if (locations != null)
        {
            locations.remove(location);
            if (locations.isEmpty()) BY_NETWORK_FAMILY.remove(key);
        }
    }

    private static String family(BaseNetFurnaceBlockEntity<?> furnace)
    {
        return NativeFurnaceFamily.forClass(furnace.getClass());
    }

    private record Location(ResourceKey<Level> dimension, BlockPos position)
    {
        private Location { position = position.immutable(); }
    }

    private record Entry(ResourceKey<Level> dimension, BlockPos position, int networkId, String family)
    {
        private Entry { position = position.immutable(); }
    }

    private record NetworkFamily(int networkId, String family) {}

    public record NativeFurnace(ServerLevel level, BaseNetFurnaceBlockEntity<?> blockEntity, String family) {}
}
