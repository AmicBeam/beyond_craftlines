package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestBindingVisualsPayload() implements CustomPacketPayload
{
    public static final Type<RequestBindingVisualsPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "request_binding_visuals"));
    public static final StreamCodec<ByteBuf, RequestBindingVisualsPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestBindingVisualsPayload());

    public static void handle(RequestBindingVisualsPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                BindingVisualsPayload.sendTo(player);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
