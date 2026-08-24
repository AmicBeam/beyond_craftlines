package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeFieldWhitelistRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Synchronizes server datapack field whitelists to the client-side recipe planner. */
public record RecipeFieldWhitelistPayload(List<String> entries) implements CustomPacketPayload
{
    public static final Type<RecipeFieldWhitelistPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "recipe_field_whitelist"));
    public static final StreamCodec<ByteBuf, RecipeFieldWhitelistPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(10_000), 128),
            RecipeFieldWhitelistPayload::entries, RecipeFieldWhitelistPayload::new);

    public static RecipeFieldWhitelistPayload snapshot()
    { return new RecipeFieldWhitelistPayload(RecipeFieldWhitelistRegistry.encodedEntries()); }

    public static void handle(RecipeFieldWhitelistPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> RecipeFieldWhitelistRegistry.applySyncedEntries(payload.entries())); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
