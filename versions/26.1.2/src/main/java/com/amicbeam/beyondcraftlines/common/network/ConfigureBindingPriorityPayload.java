package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ConfigureBindingPriorityPayload(long position, int priority) implements CustomPacketPayload
{
    public static final Type<ConfigureBindingPriorityPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "configure_binding_priority"));
    public static final StreamCodec<ByteBuf, ConfigureBindingPriorityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ConfigureBindingPriorityPayload::position,
            ByteBufCodecs.VAR_INT, ConfigureBindingPriorityPayload::priority,
            ConfigureBindingPriorityPayload::new);

    public static void handle(ConfigureBindingPriorityPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProvisionerConfigMenu menu)) return;
            BlockPos position = BlockPos.of(payload.position());
            if (!position.equals(menu.position()) || player.blockPosition().distSqr(position) > 64) return;
            boolean configured = DeviceBindingRegistry.configurePriority(
                    player, position, payload.priority(), menu.isBoundMachineConfiguration());
            String messageKey = menu.isBoundMachineConfiguration()
                    ? configured ? "message.beyond_craftlines.bound_machine_configured"
                    : "error.beyond_craftlines.bound_machine_config_failed"
                    : configured ? "message.beyond_craftlines.provisioner_configured"
                    : "error.beyond_craftlines.provisioner_config_failed";
            Component message = Component.translatable(messageKey);
            if (configured) player.sendOverlayMessage(message); else player.sendSystemMessage(message);
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
