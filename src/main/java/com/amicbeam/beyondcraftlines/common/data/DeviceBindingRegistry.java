package com.amicbeam.beyondcraftlines.common.data;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DeviceBindingRegistry
{
    private DeviceBindingRegistry() {}

    public static Optional<BindingRecord> find(BindingSavedData data, ResourceKey<Level> dimension, BlockPos position)
    {
        return Optional.ofNullable(data.at(dimension, position));
    }

    public static Optional<BindingRecord> toggle(Player player, BlockPos position, BlockState state)
    {
        if (player.level().isClientSide() || player.getServer() == null) return Optional.empty();

        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net == null || !net.isManager(player)) return Optional.empty();

        BindingSavedData data = BindingSavedData.get(player.getServer());
        ResourceKey<Level> dimension = player.level().dimension();
        Optional<BindingRecord> existing = find(data, dimension, position);
        if (existing.isPresent())
        {
            data.remove(dimension, position);
            return Optional.empty();
        }

        BindingRecord record = new BindingRecord(
                UUID.randomUUID(), player.getUUID(), net.getId(), dimension, position,
                DeviceType.fromBlockId(BuiltInRegistries.BLOCK
                        .getKey(state.getBlock()).toString()), Set.of(),
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                "", false, player.level().getGameTime());
        data.add(record);
        return Optional.of(record);
    }

    public static void removeAt(net.minecraft.server.MinecraftServer server, ResourceKey<Level> dimension, BlockPos position)
    {
        BindingSavedData.get(server).remove(dimension, position);
    }
}
