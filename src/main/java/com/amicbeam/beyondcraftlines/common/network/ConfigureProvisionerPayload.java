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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public record ConfigureProvisionerPayload(long position, List<String> selectedTypes,
                                          List<String> selectedGroups, int priority)
        implements CustomPacketPayload
{
    public static final Type<ConfigureProvisionerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "configure_provisioner"));
    public static final StreamCodec<ByteBuf, ConfigureProvisionerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ConfigureProvisionerPayload::position,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(256), 32),
            ConfigureProvisionerPayload::selectedTypes,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(384), 512),
            ConfigureProvisionerPayload::selectedGroups,
            ByteBufCodecs.VAR_INT, ConfigureProvisionerPayload::priority,
            ConfigureProvisionerPayload::new);

    public static ConfigureProvisionerPayload of(BlockPos position, Set<ResourceLocation> selected,
                                                  Map<ResourceLocation, Set<String>> groups,
                                                  int priority)
    {
        return new ConfigureProvisionerPayload(position.asLong(),
                selected.stream().map(Object::toString).sorted().limit(32).toList(),
                selected.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                        .flatMap(type -> groups.getOrDefault(type, Set.of()).stream().sorted()
                                .map(group -> type + "|" + group)).limit(512).toList(),
                priority);
    }

    public static void handle(ConfigureProvisionerPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProvisionerConfigMenu menu)) return;
            BlockPos position = BlockPos.of(payload.position());
            if (!position.equals(menu.position()) || player.blockPosition().distSqr(position) > 64) return;
            LinkedHashSet<ResourceLocation> selected = new LinkedHashSet<>();
            payload.selectedTypes().stream().limit(32).map(ResourceLocation::tryParse)
                    .filter(java.util.Objects::nonNull).forEach(selected::add);
            boolean manualSelection = menu.allowsManualRecipeSelection();
            Set<ResourceLocation> mappedAcceptedTypes = menu.isBoundMachineConfiguration()
                    ? com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes
                    .directBindable(selected)
                    : Set.copyOf(selected);
            Set<ResourceLocation> acceptedTypes = manualSelection ? Set.copyOf(selected) : mappedAcceptedTypes;
            if (!selected.isEmpty() && acceptedTypes.size() != selected.size())
            {
                var missing = selected.stream().filter(type -> !acceptedTypes.contains(type))
                        .map(Object::toString).sorted().toList();
                player.displayClientMessage(Component.translatable(
                        "error.beyond_craftlines.recipe_type_mapping_failed", String.join(", ", missing)), false);
                return;
            }
            if (!menu.acceptsRecipeSelection(selected)) return;
            Map<ResourceLocation, Set<String>> selectedGroups = new HashMap<>();
            boolean invalidGroups = false;
            for (String encoded : payload.selectedGroups().stream().limit(512).toList())
            {
                int separator = encoded.indexOf('|');
                if (separator <= 0 || separator == encoded.length() - 1)
                { invalidGroups = true; continue; }
                ResourceLocation type = ResourceLocation.tryParse(encoded.substring(0, separator));
                String group = encoded.substring(separator + 1);
                if (type == null || !selected.contains(type)
                        || !menu.availableGroups().getOrDefault(type, Set.of()).contains(group))
                { invalidGroups = true; continue; }
                selectedGroups.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(group);
            }
            boolean configured = !invalidGroups && (menu.isBoundMachineConfiguration()
                    ? DeviceBindingRegistry.configureBoundMachine(
                            player, position, selected, selectedGroups, payload.priority())
                    : DeviceBindingRegistry.configureProvisioner(
                            player, position, selected, selectedGroups, payload.priority(), manualSelection));
            String message = menu.isBoundMachineConfiguration()
                    ? configured ? "message.beyond_craftlines.bound_machine_configured"
                    : "error.beyond_craftlines.bound_machine_config_failed"
                    : configured ? "message.beyond_craftlines.provisioner_configured"
                    : "error.beyond_craftlines.provisioner_config_failed";
            player.displayClientMessage(Component.translatable(message), configured);
            if (configured) BindingVisualsPayload.broadcast(player.serverLevel());
            if (configured && manualSelection)
            {
                com.amicbeam.beyondcraftlines.common.block.CraftlineProvisionerBlock
                        .openConfiguration(player, position);
            }
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
