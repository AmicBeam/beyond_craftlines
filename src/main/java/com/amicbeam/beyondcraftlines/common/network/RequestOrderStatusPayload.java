package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderSavedData;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestOrderStatusPayload() implements CustomPacketPayload
{
    public static final Type<RequestOrderStatusPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "request_order_status"));
    public static final StreamCodec<ByteBuf, RequestOrderStatusPayload> STREAM_CODEC = StreamCodec.unit(new RequestOrderStatusPayload());

    public static void handle(RequestOrderStatusPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ListTag list = new ListTag();
            RecipeOrderSavedData.get(player.server).forOwner(player.getUUID()).stream()
                    .sorted(java.util.Comparator.comparingLong(com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderJob::createdAt).reversed())
                    .limit(20).forEach(job -> {
                        CompoundTag value = new CompoundTag(); value.putUUID("id", job.id());
                        value.putString("target", job.target().toString()); value.putLong("requested", job.requested());
                        value.putInt("next", job.nextStep()); value.putInt("total", job.steps().size());
                        value.putBoolean("blocking_mode", job.blockingMode());
                        value.putString("status", job.status().name()); value.putString("message", job.message()); list.add(value);
                    });
            CompoundTag root = new CompoundTag(); root.put("orders", list);
            PacketDistributor.sendToPlayer(player, new OrderStatusPayload(root));
        });
    }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
