package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public record DashboardStatusPayload(CompoundTag data) implements CustomPacketPayload
{
    public static final Type<DashboardStatusPayload> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    BeyondCraftlines.MOD_ID, "dashboard_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardStatusPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG,
                    DashboardStatusPayload::data, DashboardStatusPayload::new);
    public static Consumer<CompoundTag> clientReceiver = ignored -> {};

    public static void handle(DashboardStatusPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload.data())); }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
