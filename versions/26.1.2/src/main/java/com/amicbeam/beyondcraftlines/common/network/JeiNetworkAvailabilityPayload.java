package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public record JeiNetworkAvailabilityPayload(boolean available) implements CustomPacketPayload
{
    public static final Type<JeiNetworkAvailabilityPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "jei_network_availability"));
    public static final StreamCodec<ByteBuf, JeiNetworkAvailabilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, JeiNetworkAvailabilityPayload::available,
            JeiNetworkAvailabilityPayload::new);
    public static Consumer<Boolean> clientReceiver = ignored -> {};

    public static void handle(JeiNetworkAvailabilityPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload.available())); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
