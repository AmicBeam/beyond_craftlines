package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public record NetworkAmountPayload(String itemId, long amount) implements CustomPacketPayload
{
    public static final Type<NetworkAmountPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "network_amount"));
    public static final StreamCodec<ByteBuf, NetworkAmountPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NetworkAmountPayload::itemId,
            ByteBufCodecs.VAR_LONG, NetworkAmountPayload::amount,
            NetworkAmountPayload::new);
    public static BiConsumer<String, Long> clientReceiver = (ignoredId, ignoredAmount) -> {};

    public static void handle(NetworkAmountPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload.itemId(), payload.amount())); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
