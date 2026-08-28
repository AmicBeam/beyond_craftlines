package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardRedstoneMode;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardStockMode;
import com.amicbeam.beyondcraftlines.common.menu.DashboardConfigMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ConfigureDashboardPayload(BlockPos position, IStackKey<?> target, long desired,
                                        String stockMode, String redstoneMode) implements CustomPacketPayload
{
    public static final Type<ConfigureDashboardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "configure_dashboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureDashboardPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureDashboardPayload::position,
            IStackKey.STREAM_CODEC, ConfigureDashboardPayload::target,
            ByteBufCodecs.VAR_LONG, ConfigureDashboardPayload::desired,
            ByteBufCodecs.stringUtf8(16), ConfigureDashboardPayload::stockMode,
            ByteBufCodecs.stringUtf8(16), ConfigureDashboardPayload::redstoneMode,
            ConfigureDashboardPayload::new);

    public static void handle(ConfigureDashboardPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DashboardConfigMenu menu)
                    || !menu.position().equals(payload.position())
                    || !(player.level().getBlockEntity(payload.position()) instanceof CraftlineDashboardBlockEntity dashboard))
                return;
            dashboard.configure(player, payload.target(), payload.desired(),
                    DashboardStockMode.byId(payload.stockMode()),
                    DashboardRedstoneMode.byId(payload.redstoneMode()));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
