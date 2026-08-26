package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Synchronizes server datapack recipe I/O profiles to the client-side recipe planner. */
public record RecipeIoProfilePayload(List<String> entries) implements CustomPacketPayload
{
    public static final Type<RecipeIoProfilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "recipe_io_profiles"));
    public static final StreamCodec<ByteBuf, RecipeIoProfilePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(10_000), 128),
            RecipeIoProfilePayload::entries, RecipeIoProfilePayload::new);

    public static RecipeIoProfilePayload snapshot()
    { return new RecipeIoProfilePayload(RecipeIoProfileRegistry.encodedEntries()); }

    public static void handle(RecipeIoProfilePayload payload, IPayloadContext context)
    { context.enqueueWork(() -> RecipeIoProfileRegistry.applySyncedEntries(payload.entries())); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
