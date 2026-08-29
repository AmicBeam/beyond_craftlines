package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record BindMachinePayload(long targetPosition, int targetFace, List<String> jeiRecipeTypes,
                                 List<String> inputGroups, boolean remove) implements CustomPacketPayload
{
    public static final Type<BindMachinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            BeyondCraftlines.MOD_ID, "bind_machine"));
    public static final StreamCodec<ByteBuf, BindMachinePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BindMachinePayload::targetPosition,
            ByteBufCodecs.VAR_INT, BindMachinePayload::targetFace,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(256), 32),
            BindMachinePayload::jeiRecipeTypes,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(384), 512),
            BindMachinePayload::inputGroups,
            ByteBufCodecs.BOOL, BindMachinePayload::remove,
            BindMachinePayload::new);

    public static BindMachinePayload of(BlockPos target, Set<ResourceLocation> types,
                                        net.minecraft.core.Direction face, List<String> inputGroups, boolean remove)
    { return new BindMachinePayload(target.asLong(), face.get3DDataValue(),
            types.stream().map(Object::toString).sorted().limit(32).toList(),
            inputGroups.stream().limit(512).toList(), remove); }

    public static void handle(BindMachinePayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos target = BlockPos.of(payload.targetPosition());
            if (!player.level().isLoaded(target) || player.blockPosition().distSqr(target) > 64) return;
            ItemStack linker = player.getMainHandItem().is(CraftlinesItems.NETWORK_LINKER.get())
                    ? player.getMainHandItem() : player.getOffhandItem();
            if (!linker.is(CraftlinesItems.NETWORK_LINKER.get())) return;
            LinkedHashSet<ResourceLocation> types = new LinkedHashSet<>();
            payload.jeiRecipeTypes().stream().limit(32).map(ResourceLocation::tryParse)
                    .filter(java.util.Objects::nonNull).forEach(types::add);
            com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry
                    .rememberEncoded(payload.inputGroups());
            boolean connectionMode = DeviceBindingRegistry.hasProvisionerConnectionSelection(player);
            boolean provisionerRecipeMode = DeviceBindingRegistry.hasProvisionerRecipeSelection(player);
            if (payload.remove() && !connectionMode)
            {
                boolean removed = DeviceBindingRegistry.unbind(player, target);
                player.displayClientMessage(Component.translatable(removed
                        ? "message.beyond_craftlines.device_unbound"
                        : "error.beyond_craftlines.machine_not_bound_or_denied"), false);
                if (removed)
                {
                    BindingVisualsPayload.broadcast(player.serverLevel());
                }
                return;
            }
            boolean supported = provisionerRecipeMode
                    ? !types.isEmpty()
                    : !types.isEmpty() && com.amicbeam.beyondcraftlines.common.crafting
                    .VanillaProvisionerRecipeTypes.directBindable(types).size() == types.size();
            if (!connectionMode && types.isEmpty())
            {
                player.displayClientMessage(Component.translatable(
                        "error.beyond_craftlines.machine_recipe_type_unknown",
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                                player.level().getBlockState(target).getBlock())), false);
                return;
            }
            if (!connectionMode && !supported)
            {
                player.displayClientMessage(Component.translatable(
                        "error.beyond_craftlines.recipe_type_mapping_failed",
                        types.stream().map(Object::toString).sorted().collect(java.util.stream.Collectors.joining(", "))), false);
                return;
            }
            net.minecraft.core.Direction targetFace = net.minecraft.core.Direction
                    .from3DDataValue(payload.targetFace());
            var role = payload.remove()
                    ? com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity
                    .ConnectionRole.EXTRACT
                    : com.amicbeam.beyondcraftlines.common.runtime.CraftlineProvisionerBlockEntity
                    .ConnectionRole.SUPPLY;
            var binding = DeviceBindingRegistry.bindMachine(player, target, targetFace, types, role);
            var result = binding.result();
            if (binding.isSuccess() && result.deviceType()
                    == com.amicbeam.beyondcraftlines.common.data.DeviceType.EXTERNAL_RECIPE_MACHINE)
            {
                String selectedType = result.jeiRecipeTypes().stream().map(Object::toString)
                        .sorted().findFirst().orElse("");
                PacketDistributor.sendToPlayer(player, new BindMachineFeedbackPayload(selectedType));
                BindingVisualsPayload.broadcast(player.serverLevel());
                return;
            }
            String message = binding.isSuccess() && result.connectionEdit() != null
                    ? switch (result.connectionEdit())
                    {
                        case ADDED -> "message.beyond_craftlines.provisioner_device_connected";
                        case UPDATED -> "message.beyond_craftlines.provisioner_device_face_updated";
                        case EXTRACTING -> "message.beyond_craftlines.provisioner_device_extracting";
                        case REMOVED -> "message.beyond_craftlines.provisioner_device_disconnected";
                        case LIMIT_REACHED -> "error.beyond_craftlines.provisioner_connection_limit";
                    }
                    : binding.isSuccess()
                    ? result.deviceType()
                    == com.amicbeam.beyondcraftlines.common.data.DeviceType.PROVISIONER_RECIPE_BINDING
                    ? result.autoSelected()
                    ? "message.beyond_craftlines.provisioner_target_bound_single"
                    : "message.beyond_craftlines.provisioner_target_bound"
                    : "message.beyond_craftlines.machine_bound"
                    : binding.failure().messageKey();
            Component feedback = binding.isSuccess() && result.connectionEdit() != null
                    ? Component.translatable(message,
                    Component.translatable("direction.minecraft." + targetFace.getName()))
                    : !binding.isSuccess() || result.deviceType()
                    == com.amicbeam.beyondcraftlines.common.data.DeviceType.PROVISIONER_RECIPE_BINDING
                    ? Component.translatable(message)
                    : Component.translatable(message, String.join(", ", result.recipeFamilies()));
            player.displayClientMessage(feedback, false);
            if (binding.isSuccess()) BindingVisualsPayload.broadcast(player.serverLevel());
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
