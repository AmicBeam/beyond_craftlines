package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.menu.DashboardConfigMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenDashboardRecipePayload(BlockPos position) implements CustomPacketPayload
{
    public static final Type<OpenDashboardRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_dashboard_recipe"));
    public static final StreamCodec<ByteBuf, OpenDashboardRecipePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenDashboardRecipePayload::position, OpenDashboardRecipePayload::new);

    public static void handle(OpenDashboardRecipePayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DashboardConfigMenu menu)
                    || !menu.position().equals(payload.position())
                    || !(player.level().getBlockEntity(payload.position()) instanceof CraftlineDashboardBlockEntity dashboard)
                    || !dashboard.mayConfigure(player) || dashboard.target().isEmpty()) return;
            int networkId = dashboard.getNetId();
            var families = DeviceBindingRegistry.availableFamilies(player.getServer(), networkId);
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new CraftlineOrderMenu(id, inventory, networkId, dashboard.target(), null,
                            false, families, payload.position(), dashboard.recipe().blockingMode(),
                            dashboard.desiredAmount(), dashboard.stockMode().id()),
                    Component.translatable("menu.beyond_craftlines.dashboard_recipe")), buffer -> {
                        buffer.writeVarInt(networkId);
                        IStackKey.STREAM_CODEC.encode(buffer, dashboard.target());
                        buffer.writeUtf("");
                        buffer.writeBoolean(false);
                        buffer.writeVarInt(families.size());
                        families.stream().sorted().forEach(buffer::writeUtf);
                        buffer.writeBoolean(true);
                        buffer.writeBlockPos(payload.position());
                        buffer.writeBoolean(dashboard.recipe().blockingMode());
                        buffer.writeVarLong(dashboard.desiredAmount());
                        buffer.writeUtf(dashboard.stockMode().id(), 16);
                    });
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
