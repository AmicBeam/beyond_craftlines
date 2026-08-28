package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.DashboardStatusMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardIndex;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderJob;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderSavedData;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestDashboardStatusPayload(int networkId) implements CustomPacketPayload
{
    public static final Type<RequestDashboardStatusPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "request_dashboard_status"));
    public static final StreamCodec<ByteBuf, RequestDashboardStatusPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RequestDashboardStatusPayload::networkId,
                    RequestDashboardStatusPayload::new);

    public static void handle(RequestDashboardStatusPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DashboardStatusMenu menu)
                    || menu.networkId() != payload.networkId() || !menu.canAccessNetwork(player)) return;
            var server = player.level().getServer();
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            RecipeOrderSavedData orders = RecipeOrderSavedData.get(server);
            for (var dashboard : CraftlineDashboardIndex.active(server, payload.networkId()).stream()
                    .limit(256).toList())
            {
                CompoundTag value = new CompoundTag();
                value.putString("dimension", dashboard.getLevel().dimension().identifier().toString());
                value.putInt("x", dashboard.getBlockPos().getX());
                value.putInt("y", dashboard.getBlockPos().getY());
                value.putInt("z", dashboard.getBlockPos().getZ());
                writeKey(value, dashboard.target(), player);
                value.putLong("observed", dashboard.lastObserved());
                value.putLong("desired", dashboard.desiredAmount());
                value.putString("stock_mode", dashboard.stockMode().id());
                value.putString("redstone_mode", dashboard.redstoneMode().id());
                value.putString("error", dashboard.lastError());
                RecipeOrderJob active = dashboard.activeOrder() == null ? null : orders.get(dashboard.activeOrder());
                value.putBoolean("automatic_order", active != null && !terminal(active.status()));
                list.add(value);
            }
            root.put("dashboards", list);
            PacketDistributor.sendToPlayer(player, new DashboardStatusPayload(root));
        });
    }

    private static void writeKey(CompoundTag owner, IStackKey<?> key, ServerPlayer player)
    { owner.putString("key_type", key.getTypeId().toString()); owner.put("key", key.serializeNBT(player.registryAccess())); }
    private static boolean terminal(RecipeOrderJob.Status status)
    { return status == RecipeOrderJob.Status.COMPLETE || status == RecipeOrderJob.Status.CANCELLED
            || status == RecipeOrderJob.Status.ERROR; }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
