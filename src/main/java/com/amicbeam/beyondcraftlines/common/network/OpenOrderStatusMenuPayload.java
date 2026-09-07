package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineStatusMenu;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderJob;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenOrderStatusMenuPayload() implements CustomPacketPayload
{
    public static final Type<OpenOrderStatusMenuPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_order_status"));
    public static final StreamCodec<ByteBuf, OpenOrderStatusMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenOrderStatusMenuPayload());

    public static void handle(OpenOrderStatusMenuPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DimensionsNetMenu dimensionsMenu)) return;
            DimensionsNet network = dimensionsMenu.storage instanceof UnifiedStorage storage ? storage.getNet() : null;
            if (network == null)
            {
                player.displayClientMessage(Component.translatable("error.beyond_craftlines.network_required"), false);
                return;
            }
            open(player, network.getId());
        });
    }

    public static void open(ServerPlayer player, int networkId)
    { open(player, networkId, null); }

    public static void open(ServerPlayer player, int networkId, RecipeOrderJob initialOrder)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        if (network == null || !(network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID()))) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new CraftlineStatusMenu(id, inventory, networkId, initialOrder == null ? null
                        : new CraftlineStatusMenu.InitialOrder(initialOrder.id(), initialOrder.target().toString(),
                        initialOrder.targetKey(), initialOrder.requested(), initialOrder.blockingMode())),
                Component.translatable("menu.beyond_craftlines.status")), buffer -> {
                    buffer.writeVarInt(networkId);
                    buffer.writeBoolean(initialOrder != null);
                    if (initialOrder != null)
                    {
                        buffer.writeUUID(initialOrder.id());
                        buffer.writeUtf(initialOrder.target().toString(), 256);
                        IStackKey.STREAM_CODEC.encode(
                                (net.minecraft.network.RegistryFriendlyByteBuf) buffer, initialOrder.targetKey());
                        buffer.writeVarLong(initialOrder.requested());
                        buffer.writeBoolean(initialOrder.blockingMode());
                    }
                });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
