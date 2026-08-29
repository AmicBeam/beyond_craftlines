package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.JeiOnlyRecipeTypeRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Synchronizes datapack-enabled JEI-only category ids to the client integration. */
public record JeiOnlyRecipeTypesPayload(List<String> recipeTypes,
                                        boolean serverRecipeValidationEnabled) implements CustomPacketPayload
{
    public static final Type<JeiOnlyRecipeTypesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "jei_only_recipe_types"));
    public static final StreamCodec<ByteBuf, JeiOnlyRecipeTypesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(256), 1_024),
            JeiOnlyRecipeTypesPayload::recipeTypes,
            ByteBufCodecs.BOOL, JeiOnlyRecipeTypesPayload::serverRecipeValidationEnabled,
            JeiOnlyRecipeTypesPayload::new);

    public static JeiOnlyRecipeTypesPayload snapshot(net.minecraft.server.MinecraftServer server)
    {
        boolean enabled = com.amicbeam.beyondcraftlines.CraftlinesConfig.VERIFY_SERVER_RECIPE_TYPES.get();
        JeiOnlyRecipeTypeRegistry.setServerRecipeValidationEnabled(enabled);
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>(
                JeiOnlyRecipeTypeRegistry.datapackRecipeTypes());
        Set<String> loadedFamilies = com.amicbeam.beyondcraftlines.common.crafting
                .RecipePlanningService.loadedFamilies(server.overworld());
        com.amicbeam.beyondcraftlines.common.data.BindingSavedData.get(server).records().stream()
                .forEach(record -> record.jeiRecipeTypes().stream()
                        .filter(type -> !enabled || (record.recipeFamilies().contains(type.toString())
                                && com.amicbeam.beyondcraftlines.common.crafting.JeiRecipeFamilyRegistry
                                .resolve(Set.of(type), loadedFamilies).isEmpty()))
                        .map(Object::toString).forEach(types::add));
        return new JeiOnlyRecipeTypesPayload(types.stream().sorted().limit(1_024).toList(), enabled);
    }

    public static void handle(JeiOnlyRecipeTypesPayload payload, IPayloadContext context)
    { context.enqueueWork(() -> {
        JeiOnlyRecipeTypeRegistry.applySyncedTypes(payload.recipeTypes());
        JeiOnlyRecipeTypeRegistry.setServerRecipeValidationEnabled(payload.serverRecipeValidationEnabled());
    }); }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
