package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ExtractProvisionerPayload(long position) implements CustomPacketPayload
{
    public static final Type<ExtractProvisionerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "extract_provisioner"));
    public static final StreamCodec<ByteBuf, ExtractProvisionerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ExtractProvisionerPayload::position, ExtractProvisionerPayload::new);

    public static void handle(ExtractProvisionerPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProvisionerConfigMenu menu)) return;
            BlockPos position = BlockPos.of(payload.position());
            if (!position.equals(menu.position()) || player.blockPosition().distSqr(position) > 64) return;
            if (player.level().getBlockEntity(position) instanceof CraftlineProvisionerBlockEntity provisioner)
                provisioner.giveOneItemStack(player);
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
