package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SubmitOrderPayload(String itemId, long count, boolean blockingMode) implements CustomPacketPayload
{
    public static final Type<SubmitOrderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "submit_order"));
    public static final StreamCodec<ByteBuf, SubmitOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SubmitOrderPayload::itemId,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::count,
            ByteBufCodecs.BOOL, SubmitOrderPayload::blockingMode, SubmitOrderPayload::new);

    public static void handle(SubmitOrderPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)) return;
            try
            {
                long count = Math.max(1, payload.count());
                var job = RecipeOrderService.enqueue(player.serverLevel(), player.getUUID(), menu.networkId(),
                        ResourceLocation.parse(payload.itemId()), count, payload.blockingMode());
                player.displayClientMessage(Component.translatable(
                        "message.beyond_craftlines.order_queued", job.id().toString()), false);
            }
            catch (RuntimeException exception)
            {
                player.displayClientMessage(Component.translatable("error.beyond_craftlines.order_failed", exception.getMessage()), false);
            }
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
