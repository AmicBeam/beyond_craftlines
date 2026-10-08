package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public record BindMachineFeedbackPayload(String jeiRecipeType) implements CustomPacketPayload
{
    public static final Type<BindMachineFeedbackPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "bind_machine_feedback"));
    public static final StreamCodec<ByteBuf, BindMachineFeedbackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), BindMachineFeedbackPayload::jeiRecipeType,
            BindMachineFeedbackPayload::new);
    public static Consumer<String> clientReceiver = ignored -> {};

    public static void handle(BindMachineFeedbackPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload.jeiRecipeType())); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
