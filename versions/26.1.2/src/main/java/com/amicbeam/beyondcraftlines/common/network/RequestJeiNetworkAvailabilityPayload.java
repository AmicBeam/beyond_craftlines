package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestJeiNetworkAvailabilityPayload() implements CustomPacketPayload
{
    public static final Type<RequestJeiNetworkAvailabilityPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "request_jei_network_availability"));
    public static final StreamCodec<ByteBuf, RequestJeiNetworkAvailabilityPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestJeiNetworkAvailabilityPayload());

    public static void handle(RequestJeiNetworkAvailabilityPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                PacketDistributor.sendToPlayer(player, new JeiNetworkAvailabilityPayload(
                        DimensionsNet.getPrimaryNetFromPlayer(player) != null));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
