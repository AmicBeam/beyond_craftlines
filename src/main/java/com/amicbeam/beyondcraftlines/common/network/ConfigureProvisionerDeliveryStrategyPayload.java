package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.amicbeam.beyondcraftlines.common.runtime.ProvisionerDeliveryStrategy;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ConfigureProvisionerDeliveryStrategyPayload(long position, int strategy)
        implements CustomPacketPayload
{
    public static final Type<ConfigureProvisionerDeliveryStrategyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID,
                    "configure_provisioner_delivery_strategy"));
    public static final StreamCodec<ByteBuf, ConfigureProvisionerDeliveryStrategyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, ConfigureProvisionerDeliveryStrategyPayload::position,
                    ByteBufCodecs.VAR_INT, ConfigureProvisionerDeliveryStrategyPayload::strategy,
                    ConfigureProvisionerDeliveryStrategyPayload::new);

    public static void handle(ConfigureProvisionerDeliveryStrategyPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProvisionerConfigMenu menu)
                    || menu.isBoundMachineConfiguration()
                    || !ProvisionerDeliveryStrategy.isValidId(payload.strategy())) return;
            BlockPos position = BlockPos.of(payload.position());
            if (!position.equals(menu.position()) || player.blockPosition().distSqr(position) > 64
                    || !(player.level().getBlockEntity(position)
                    instanceof CraftlineProvisionerBlockEntity provisioner)) return;
            DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
            if (network == null || !network.isManager(player)) return;
            provisioner.setDeliveryStrategy(ProvisionerDeliveryStrategy.fromId(payload.strategy()));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
