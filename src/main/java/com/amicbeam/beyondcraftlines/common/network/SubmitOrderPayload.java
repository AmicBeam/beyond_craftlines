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
    private static final String LAST_SUBMIT_TICK = "beyond_craftlines_last_submit_tick";
    public static final Type<SubmitOrderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "submit_order"));
    public static final StreamCodec<ByteBuf, SubmitOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), SubmitOrderPayload::itemId,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::count,
            ByteBufCodecs.BOOL, SubmitOrderPayload::blockingMode, SubmitOrderPayload::new);

    public static void handle(SubmitOrderPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)) return;
            try
            {
                long now = player.serverLevel().getGameTime();
                long last = player.getPersistentData().getLong(LAST_SUBMIT_TICK);
                int cooldown = com.amicbeam.beyondcraftlines.CraftlinesConfig.ORDER_SUBMIT_COOLDOWN_TICKS.get();
                if (cooldown > 0 && last > 0 && now >= last && now - last < cooldown)
                    throw new IllegalStateException("orders are being submitted too quickly");
                player.getPersistentData().putLong(LAST_SUBMIT_TICK, now);
                long count = Math.max(1, payload.count());
                ResourceLocation target = ResourceLocation.parse(payload.itemId());
                if (menu.recipeForOutput(target) == null) throw new IllegalArgumentException("target is not available in this order menu");
                var job = RecipeOrderService.enqueue(player.serverLevel(), player.getUUID(), menu.networkId(),
                        target, count, payload.blockingMode());
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
