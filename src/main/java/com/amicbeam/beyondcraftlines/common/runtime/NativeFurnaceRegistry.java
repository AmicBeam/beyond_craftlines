package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.event.dimensionnet.NetedBlockEvent;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetBlastFurnaceBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetFurnaceBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetSmokerBlockEntity;
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

/** Runtime index of loaded BD network furnaces. No persistent copy is needed. */
public final class NativeFurnaceRegistry
{
    private static final Map<Location, Entry> LOADED = new HashMap<>();

    private NativeFurnaceRegistry() {}

    public static void onBound(NetedBlockEvent.Bound event)
    {
        register(event.getBlockEntity());
    }

    public static void onUnbound(NetedBlockEvent.Unbound event)
    {
        LOADED.remove(new Location(event.getLevel().dimension(), event.getPos()));
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
        LOADED.keySet().removeIf(location -> location.dimension().equals(level.dimension())
                && (location.position().getX() >> 4) == chunkX
                && (location.position().getZ() >> 4) == chunkZ);
    }

    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel level)
            LOADED.keySet().removeIf(location -> location.dimension().equals(level.dimension()));
    }

    public static Optional<NativeFurnace> furnaceFor(MinecraftServer server, int networkId, String family)
    {
        return java.util.List.copyOf(LOADED.values()).stream()
                .filter(entry -> entry.networkId() == networkId && entry.family().equals(family))
                .sorted(Comparator.comparing((Entry entry) -> entry.dimension().location().toString())
                        .thenComparingLong(entry -> entry.position().asLong()))
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
        LOADED.put(location, new Entry(level.dimension(), furnace.getBlockPos(), furnace.getNetId(), family));
    }

    private static Optional<NativeFurnace> validate(MinecraftServer server, Entry entry)
    {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null || !level.isLoaded(entry.position())) return Optional.empty();
        BlockEntity blockEntity = level.getBlockEntity(entry.position());
        if (!(blockEntity instanceof BaseNetFurnaceBlockEntity<?> furnace)
                || furnace.getNetId() != entry.networkId() || !entry.family().equals(family(furnace)))
        {
            LOADED.remove(new Location(entry.dimension(), entry.position()));
            return Optional.empty();
        }
        return Optional.of(new NativeFurnace(level, furnace, entry.family()));
    }

    private static String family(BaseNetFurnaceBlockEntity<?> furnace)
    {
        if (furnace instanceof NetFurnaceBlockEntity) return "smelting";
        if (furnace instanceof NetBlastFurnaceBlockEntity) return "blasting";
        if (furnace instanceof NetSmokerBlockEntity) return "smoking";
        return null;
    }

    private record Location(ResourceKey<Level> dimension, BlockPos position)
    {
        private Location { position = position.immutable(); }
    }

    private record Entry(ResourceKey<Level> dimension, BlockPos position, int networkId, String family)
    {
        private Entry { position = position.immutable(); }
    }

    public record NativeFurnace(ServerLevel level, BaseNetFurnaceBlockEntity<?> blockEntity, String family) {}
}
