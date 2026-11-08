package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Stable failure code only; never send raw exception text to the client. */
public record SubmissionFailurePayload(int menuId, long nonce, String error) implements CustomPacketPayload
{
    public static final Type<SubmissionFailurePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "submission_failure"));
    public static final StreamCodec<ByteBuf, SubmissionFailurePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SubmissionFailurePayload::menuId,
            ByteBufCodecs.VAR_LONG, SubmissionFailurePayload::nonce,
            ByteBufCodecs.stringUtf8(128), SubmissionFailurePayload::error,
            SubmissionFailurePayload::new);
    public static java.util.function.Consumer<SubmissionFailurePayload> clientReceiver = ignored -> {};
    public static void handle(SubmissionFailurePayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload)); }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
