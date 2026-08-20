package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public record OrderStatusPayload(CompoundTag data) implements CustomPacketPayload
{
    public static final Type<OrderStatusPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "order_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrderStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, OrderStatusPayload::data, OrderStatusPayload::new);
    public static Consumer<CompoundTag> clientReceiver = ignored -> {};

    public static void handle(OrderStatusPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload.data())); }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
