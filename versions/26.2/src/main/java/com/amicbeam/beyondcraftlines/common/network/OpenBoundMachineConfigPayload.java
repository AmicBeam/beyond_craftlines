package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import com.amicbeam.beyondcraftlines.common.data.DeviceType;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.runtime.BoundMachineAutomation;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record OpenBoundMachineConfigPayload(long targetPosition, List<String> jeiRecipeTypes,
                                            List<String> inputGroups)
        implements CustomPacketPayload
{
    public static final Type<OpenBoundMachineConfigPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_bound_machine_config"));
    public static final StreamCodec<ByteBuf, OpenBoundMachineConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, OpenBoundMachineConfigPayload::targetPosition,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(256), 32),
            OpenBoundMachineConfigPayload::jeiRecipeTypes,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(384), 512),
            OpenBoundMachineConfigPayload::inputGroups,
            OpenBoundMachineConfigPayload::new);

    public static OpenBoundMachineConfigPayload of(BlockPos target, Set<Identifier> types,
                                                    List<String> inputGroups)
    {
        return new OpenBoundMachineConfigPayload(target.asLong(),
                types.stream().map(Object::toString).sorted().limit(32).toList(),
                inputGroups.stream().limit(512).toList());
    }

    public static void handle(OpenBoundMachineConfigPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) return;
            BlockPos position = BlockPos.of(payload.targetPosition());
            if (!level.isLoaded(position) || player.blockPosition().distSqr(position) > 64
                    || (!player.getMainHandItem().is(CraftlinesItems.NETWORK_LINKER.get())
                    && !player.getOffhandItem().is(CraftlinesItems.NETWORK_LINKER.get()))) return;
            if (level.getBlockState(position)
                    .is(com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks.CRAFTLINE_PROVISIONER.get()))
            {
                com.amicbeam.beyondcraftlines.common.block.CraftlineProvisionerBlock
                        .openConfiguration(player, position);
                return;
            }

            var binding = BindingSavedData.get(level.getServer()).at(level.dimension(), position);
            if (binding == null || binding.deviceType() != DeviceType.EXTERNAL_RECIPE_MACHINE
                    || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock())
                    .equals(binding.lastBlockId())
                    || !BoundMachineAutomation.isAutomatable(level, position)) return;
            DimensionsNet network = DimensionsNet.getNetFromId(binding.networkId());
            if (network == null || !network.isManager(player)) return;

            LinkedHashSet<Identifier> requested = new LinkedHashSet<>(binding.jeiRecipeTypes());
            payload.jeiRecipeTypes().stream().limit(32).map(Identifier::tryParse)
                    .filter(java.util.Objects::nonNull).forEach(requested::add);
            com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry
                    .rememberEncoded(payload.inputGroups());
            Set<Identifier> candidates = com.amicbeam.beyondcraftlines.common.crafting
                    .VanillaProvisionerRecipeTypes.directBindable(requested);
            if (candidates.isEmpty())
            {
                player.sendSystemMessage(Component.translatable(
                        "error.beyond_craftlines.recipe_type_mapping_failed",
                        requested.stream().map(Object::toString).sorted()
                                .collect(java.util.stream.Collectors.joining(", "))));
                return;
            }
            Set<Identifier> selected = binding.jeiRecipeTypes().stream()
                    .filter(candidates::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
            Map<Identifier, Set<String>> availableGroups =
                    com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry
                            .inputGroupsByJeiType(level, candidates);
            Map<Identifier, Set<String>> selectedGroups =
                    com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry
                            .selectedGroupsByJeiType(level, candidates, binding.provisionerInputGroups());

            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new ProvisionerConfigMenu(id, inventory, position, candidates, selected,
                            availableGroups, selectedGroups, binding.priority()),
                    Component.translatable("menu.beyond_craftlines.bound_machine")), buffer ->
                    ProvisionerConfigMenu.writeOptions(buffer, position, candidates, selected,
                            availableGroups, selectedGroups, true, binding.priority()));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
