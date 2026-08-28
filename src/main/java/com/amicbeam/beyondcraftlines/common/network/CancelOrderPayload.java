package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record CancelOrderPayload(UUID orderId) implements CustomPacketPayload
{
    public static final Type<CancelOrderPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "cancel_order"));
    public static final StreamCodec<ByteBuf, CancelOrderPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CancelOrderPayload::orderId,
            CancelOrderPayload::new);
    public static void handle(CancelOrderPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                RecipeOrderService.cancel(player, payload.orderId());
        });
    }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
