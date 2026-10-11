package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.ResourceFilter;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigureResourceFilterPayload(BlockPos position, ResourceFilter filter) implements CustomPacketPayload
{
    public static final Type<ConfigureResourceFilterPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "configure_resource_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureResourceFilterPayload> STREAM_CODEC =
            StreamCodec.of((buffer, value) -> {
                buffer.writeBlockPos(value.position());
                value.filter().write(buffer);
            }, buffer -> new ConfigureResourceFilterPayload(buffer.readBlockPos(), ResourceFilter.read(buffer)));

    public static void handle(ConfigureResourceFilterPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProvisionerConfigMenu menu)
                    || !menu.position().equals(payload.position()) || !menu.stillValid(player)) return;
            BindingSavedData data = BindingSavedData.get(player.level().getServer());
            var binding = data.at(player.level().dimension(), payload.position());
            int networkId;
            if (menu.isBoundMachineConfiguration())
            {
                if (binding == null) return;
                networkId = binding.networkId();
            }
            else if (player.level().getBlockEntity(payload.position()) instanceof CraftlineProvisionerBlockEntity provisioner)
                networkId = provisioner.getNetId();
            else return;
            DimensionsNet network = DimensionsNet.getNetFromId(networkId);
            if (network == null || !network.isManager(player)) return;
            data.setResourceFilter(player.level().dimension(), payload.position(), payload.filter());
            menu.setResourceFilter(payload.filter());
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
