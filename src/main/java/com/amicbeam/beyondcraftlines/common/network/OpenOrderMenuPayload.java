package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenOrderMenuPayload(String targetItemId) implements CustomPacketPayload
{
    public static final Type<OpenOrderMenuPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_order_menu"));
    public static final StreamCodec<ByteBuf, OpenOrderMenuPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenOrderMenuPayload::targetItemId, OpenOrderMenuPayload::new);

    public static void handle(OpenOrderMenuPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DimensionsNetMenu dimensionsMenu)) return;
            ResourceLocation target = ResourceLocation.tryParse(payload.targetItemId());
            if (target == null)
            {
                player.displayClientMessage(Component.translatable("error.beyond_craftlines.invalid_order_target"), false);
                return;
            }
            DimensionsNet network = dimensionsMenu.storage instanceof com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage storage
                    ? storage.getNet() : null;
            if (network == null)
            {
                player.displayClientMessage(Component.translatable("error.beyond_craftlines.network_required"), false);
                return;
            }
            int networkId = network.getId();
            var availableFamilies = DeviceBindingRegistry.availableFamilies(player.getServer(), networkId);
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new CraftlineOrderMenu(id, inventory, networkId, target, availableFamilies),
                    Component.translatable("menu.beyond_craftlines.order")), buffer -> {
                        buffer.writeVarInt(networkId);
                        buffer.writeUtf(target.toString());
                        buffer.writeVarInt(availableFamilies.size());
                        availableFamilies.stream().sorted().forEach(buffer::writeUtf);
                    });
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
