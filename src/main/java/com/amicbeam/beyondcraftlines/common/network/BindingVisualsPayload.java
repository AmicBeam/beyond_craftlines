package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public record BindingVisualsPayload(CompoundTag data) implements CustomPacketPayload
{
    public static final Type<BindingVisualsPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "binding_visuals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindingVisualsPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, BindingVisualsPayload::data,
                    BindingVisualsPayload::new);
    public static Consumer<CompoundTag> clientReceiver = ignored -> {};

    public static void sendTo(ServerPlayer player)
    {
        PacketDistributor.sendToPlayer(player, snapshot(player.serverLevel()));
    }

    public static void broadcast(ServerLevel level)
    {
        PacketDistributor.sendToPlayersInDimension(level, snapshot(level));
    }

    private static BindingVisualsPayload snapshot(ServerLevel level)
    {
        ListTag positions = new ListTag();
        BindingSavedData.get(level.getServer()).records().stream()
                .filter(record -> record.dimension().equals(level.dimension()))
                .filter(record -> record.deviceType()
                        == com.amicbeam.beyondcraftlines.common.data.DeviceType.EXTERNAL_RECIPE_MACHINE)
                .forEach(record -> {
                    CompoundTag position = new CompoundTag();
                    position.putLong("pos", record.position().asLong());
                    position.putString("block", record.lastBlockId().toString());
                    position.putBoolean("provisioner_target", false);
                    positions.add(position);
                });
        CompoundTag root = new CompoundTag();
        root.putString("dimension", level.dimension().location().toString());
        root.put("positions", positions);
        return new BindingVisualsPayload(root);
    }

    public static void handle(BindingVisualsPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> clientReceiver.accept(payload.data()));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
