package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.DashboardStatusMenu;
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

public record OpenDashboardStatusMenuPayload() implements CustomPacketPayload
{
    public static final Type<OpenDashboardStatusMenuPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "open_dashboard_status"));
    public static final StreamCodec<ByteBuf, OpenDashboardStatusMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenDashboardStatusMenuPayload());

    public static void handle(OpenDashboardStatusMenuPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DimensionsNetMenu dimensionsMenu)) return;
            DimensionsNet network = dimensionsMenu.storage instanceof UnifiedStorage storage
                    ? storage.getNet() : null;
            if (network == null) return;
            int networkId = network.getId();
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new DashboardStatusMenu(id, inventory, networkId),
                    Component.translatable("menu.beyond_craftlines.dashboard_status")), buffer ->
                    buffer.writeVarInt(networkId));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
