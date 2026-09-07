package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record JeiNetworkAvailabilityPayload(boolean available, List<String> recipeTypes) implements CustomPacketPayload
{
    public JeiNetworkAvailabilityPayload
    { recipeTypes = List.copyOf(recipeTypes); }

    public static final Type<JeiNetworkAvailabilityPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "jei_network_availability"));
    public static final StreamCodec<ByteBuf, JeiNetworkAvailabilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, JeiNetworkAvailabilityPayload::available,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(256), 512),
            JeiNetworkAvailabilityPayload::recipeTypes,
            JeiNetworkAvailabilityPayload::new);
    public static Consumer<JeiNetworkAvailabilityPayload> clientReceiver = ignored -> {};

    public static void handle(JeiNetworkAvailabilityPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload)); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
