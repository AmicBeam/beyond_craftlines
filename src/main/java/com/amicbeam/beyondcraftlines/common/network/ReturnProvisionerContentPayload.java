package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ReturnProvisionerContentPayload(long position) implements CustomPacketPayload
{
    public static final Type<ReturnProvisionerContentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "return_provisioner_content"));
    public static final StreamCodec<ByteBuf, ReturnProvisionerContentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ReturnProvisionerContentPayload::position,
            ReturnProvisionerContentPayload::new);

    public static void handle(ReturnProvisionerContentPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProvisionerConfigMenu menu)) return;
            BlockPos position = BlockPos.of(payload.position());
            if (!position.equals(menu.position()) || player.blockPosition().distSqr(position) > 64
                    || !(player.level().getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner))
                return;
            DimensionsNet network = DimensionsNet.getNetFromId(provisioner.getNetId());
            if (network == null)
            {
                player.displayClientMessage(Component.translatable(
                        "error.beyond_craftlines.provisioner_return_no_network"), true);
                return;
            }
            provisioner.returnContentTo(network.getUnifiedStorage());
            player.displayClientMessage(Component.translatable(provisioner.isEmpty()
                    ? "message.beyond_craftlines.provisioner_returned_all"
                    : "message.beyond_craftlines.provisioner_returned_partial"), true);
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
