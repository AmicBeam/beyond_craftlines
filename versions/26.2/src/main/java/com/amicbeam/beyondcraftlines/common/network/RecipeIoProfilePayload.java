package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Synchronizes server datapack recipe I/O profiles to the client-side recipe planner. */
public record RecipeIoProfilePayload(List<String> entries, List<String> categoryBlacklist) implements CustomPacketPayload
{
    public static final Type<RecipeIoProfilePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "recipe_io_profiles"));
    public static final StreamCodec<ByteBuf, RecipeIoProfilePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(10_000), 128),
            RecipeIoProfilePayload::entries,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(256), 1024),
            RecipeIoProfilePayload::categoryBlacklist, RecipeIoProfilePayload::new);

    public RecipeIoProfilePayload
    {
        entries = List.copyOf(entries);
        categoryBlacklist = List.copyOf(categoryBlacklist);
        if (!com.amicbeam.beyondcraftlines.common.crafting.RecipeCategoryBlacklist.validList(categoryBlacklist))
            throw new IllegalArgumentException("invalid recipe category blacklist");
    }

    public static void broadcast(net.minecraft.server.MinecraftServer server)
    {
        var payload = snapshot();
        server.getPlayerList().getPlayers().forEach(player ->
                PacketDistributor.sendToPlayer(player, payload));
    }

    public static RecipeIoProfilePayload snapshot()
    { return new RecipeIoProfilePayload(RecipeIoProfileRegistry.encodedEntries(),
            com.amicbeam.beyondcraftlines.common.crafting.RecipeCategoryBlacklist.configuredEntries()); }

    public static void handle(RecipeIoProfilePayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            RecipeIoProfileRegistry.applySyncedEntries(payload.entries());
            com.amicbeam.beyondcraftlines.common.crafting.RecipeCategoryBlacklist.applySynced(payload.categoryBlacklist());
            com.amicbeam.beyondcraftlines.client.ClientPlanningCatalogWarmup.invalidate();
            com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex.refresh();
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
