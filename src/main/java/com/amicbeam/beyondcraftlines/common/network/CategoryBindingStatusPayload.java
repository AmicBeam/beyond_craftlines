package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Read-only binding query result, scoped to the requesting order menu and nonce. */
public record CategoryBindingStatusPayload(int menuId, long nonce, String category, int flags,
                                           int matches, String endpoints) implements CustomPacketPayload
{
    public static final Type<CategoryBindingStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "category_binding_status"));
    public static final StreamCodec<ByteBuf, CategoryBindingStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CategoryBindingStatusPayload::menuId,
            ByteBufCodecs.VAR_LONG, CategoryBindingStatusPayload::nonce,
            ByteBufCodecs.stringUtf8(256), CategoryBindingStatusPayload::category,
            ByteBufCodecs.VAR_INT, CategoryBindingStatusPayload::flags,
            ByteBufCodecs.VAR_INT, CategoryBindingStatusPayload::matches,
            ByteBufCodecs.stringUtf8(2048), CategoryBindingStatusPayload::endpoints,
            CategoryBindingStatusPayload::new);
    public static java.util.function.Consumer<CategoryBindingStatusPayload> clientReceiver = ignored -> {};
    public boolean matchesRequest(int expectedMenu, long expectedNonce, String expectedCategory)
    { return menuId == expectedMenu && nonce == expectedNonce && category.equals(expectedCategory); }
    public boolean available() { return (flags & 1) != 0; }
    public boolean manager() { return (flags & 2) != 0; }
    public boolean networkAvailable() { return (flags & 4) != 0; }
    public static void handle(CategoryBindingStatusPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> clientReceiver.accept(payload)); }
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
