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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record ConfigureProvisionerPayload(long position, List<String> selectedTypes)
        implements CustomPacketPayload
{
    public static final Type<ConfigureProvisionerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "configure_provisioner"));
    public static final StreamCodec<ByteBuf, ConfigureProvisionerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ConfigureProvisionerPayload::position,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(256), 32),
            ConfigureProvisionerPayload::selectedTypes, ConfigureProvisionerPayload::new);

    public static ConfigureProvisionerPayload of(BlockPos position, java.util.Set<Identifier> selected)
    {
        return new ConfigureProvisionerPayload(position.asLong(),
                selected.stream().map(Object::toString).sorted().limit(32).toList());
    }

    public static void handle(ConfigureProvisionerPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProvisionerConfigMenu menu)) return;
            BlockPos position = BlockPos.of(payload.position());
            if (!position.equals(menu.position()) || player.blockPosition().distSqr(position) > 64) return;
            LinkedHashSet<Identifier> selected = new LinkedHashSet<>();
            payload.selectedTypes().stream().limit(32).map(Identifier::tryParse)
                    .filter(java.util.Objects::nonNull).forEach(selected::add);
            boolean configured = DeviceBindingRegistry.configureProvisioner(player, position, selected);
            Component message = Component.translatable(configured
                    ? "message.beyond_craftlines.provisioner_configured"
                    : "error.beyond_craftlines.provisioner_config_failed");
            if (configured) player.sendOverlayMessage(message); else player.sendSystemMessage(message);
            if (configured) BindingVisualsPayload.broadcast(player.level());
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
