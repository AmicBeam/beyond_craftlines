package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineStatusMenu;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
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
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_order_status"));
    public static final StreamCodec<ByteBuf, OpenOrderStatusMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenOrderStatusMenuPayload());

    public static void handle(OpenOrderStatusMenuPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DimensionsNetMenu dimensionsMenu)) return;
            DimensionsNet network = DimensionsNet.getAllNetFromPlayer(player).stream()
                    .filter(net -> net.getUnifiedStorage() == dimensionsMenu.storage).findFirst().orElse(null);
            if (network == null)
            {
                player.sendSystemMessage(Component.translatable("error.beyond_craftlines.network_required"));
                return;
            }
            open(player, network.getId());
        });
    }

    public static void open(ServerPlayer player, int networkId)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        if (network == null || !(network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID()))) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new CraftlineStatusMenu(id, inventory, networkId),
                Component.translatable("menu.beyond_craftlines.status")), buffer -> buffer.writeVarInt(networkId));
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
