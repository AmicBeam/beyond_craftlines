package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.data.BindingSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public record BindingVisualsPayload(CompoundTag data) implements CustomPacketPayload
{
    public static final Type<BindingVisualsPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "binding_visuals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindingVisualsPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, BindingVisualsPayload::data,
                    BindingVisualsPayload::new);
    public static Consumer<CompoundTag> clientReceiver = ignored -> {};

    public static void sendTo(ServerPlayer player)
    {
        PacketDistributor.sendToPlayer(player, snapshot(player));
    }

    public static void broadcast(ServerLevel level)
    {
        level.players().forEach(BindingVisualsPayload::sendTo);
    }

    private static BindingVisualsPayload snapshot(ServerPlayer player)
    {
        ServerLevel level = player.level();
        ListTag positions = new ListTag();
        BindingSavedData.get(level.getServer()).records().stream()
                .filter(record -> record.dimension().equals(level.dimension()))
                .filter(record -> record.deviceType()
                        == com.amicbeam.beyondcraftlines.common.data.DeviceType.EXTERNAL_RECIPE_MACHINE)
                .forEach(record -> {
                    CompoundTag position = new CompoundTag();
                    position.putLong("pos", record.position().asLong());
                    position.putString("block", record.lastBlockId().toString());
                    position.putBoolean("provisioner_target", false);
                    positions.add(position);
                });
        CompoundTag root = new CompoundTag();
        root.putString("dimension", level.dimension().identifier().toString());
        root.put("positions", positions);
        var editingSelection = com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry
                .connectionSelection(player).orElse(null);
        ListTag boundFaces = new ListTag();
        java.util.HashSet<String> visitedProvisioners = new java.util.HashSet<>();
        BindingSavedData.get(level.getServer()).records().stream()
                .filter(record -> record.deviceType()
                        == com.amicbeam.beyondcraftlines.common.data.DeviceType.PROVISIONER_RECIPE_BINDING)
                .forEach(record -> {
                    var dimension = record.provisionerDimension() == null
                            ? record.dimension() : record.provisionerDimension();
                    var position = record.provisionerPosition() == null
                            ? record.position() : record.provisionerPosition();
                    if (!visitedProvisioners.add(dimension.identifier() + ":" + position.asLong())) return;
                    ServerLevel provisionerLevel = level.getServer().getLevel(dimension);
                    if (provisionerLevel == null || !provisionerLevel.isLoaded(position)
                            || !(provisionerLevel.getBlockEntity(position)
                            instanceof com.amicbeam.beyondcraftlines.common.runtime
                            .CraftlineProvisionerBlockEntity provisioner)) return;
                    provisioner.wirelessConnections().stream()
                            .filter(connection -> connection.dimension().equals(level.dimension()))
                            .forEach(connection -> {
                                CompoundTag encoded = new CompoundTag();
                                encoded.putLong("pos", connection.position().asLong());
                                encoded.putInt("face", connection.face().get3DDataValue());
                                encoded.putString("block", connection.blockId().toString());
                                encoded.putInt("role", connection.role().id());
                                encoded.putBoolean("editing", editingSelection != null
                                        && editingSelection.dimension().equals(dimension)
                                        && editingSelection.position().equals(position));
                                boundFaces.add(encoded);
                            });
                });
        root.put("bound_provisioner_faces", boundFaces);
        java.util.Optional.ofNullable(editingSelection)
                .filter(selection -> selection.dimension().equals(level.dimension()))
                .ifPresent(selection -> {
                    root.putLong("selected_provisioner", selection.position().asLong());
                });
        return new BindingVisualsPayload(root);
    }

    public static void handle(BindingVisualsPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> clientReceiver.accept(payload.data()));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
