package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenOrderMenuPayload(IStackKey<?> target, String recipeId, String jeiRecipeType)
        implements CustomPacketPayload
{
    public static final Type<OpenOrderMenuPayload> TYPE = new Type<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "open_order_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOrderMenuPayload> STREAM_CODEC = StreamCodec.composite(
            IStackKey.STREAM_CODEC, OpenOrderMenuPayload::target,
            ByteBufCodecs.stringUtf8(256), OpenOrderMenuPayload::recipeId,
            ByteBufCodecs.stringUtf8(256), OpenOrderMenuPayload::jeiRecipeType,
            OpenOrderMenuPayload::new);

    public static void handle(OpenOrderMenuPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            IStackKey<?> target = payload.target();
            Identifier requestedRecipe = payload.recipeId().isBlank()
                    ? null : Identifier.tryParse(payload.recipeId());
            Identifier requestedType = payload.jeiRecipeType().isBlank()
                    ? null : Identifier.tryParse(payload.jeiRecipeType());
            if (target == null || target.isEmpty()
                    || (!payload.jeiRecipeType().isBlank() && requestedType == null)
                    || (!payload.recipeId().isBlank() && requestedRecipe == null))
            {
                player.sendSystemMessage(Component.translatable("error.beyond_craftlines.invalid_order_target"));
                return;
            }
            var candidates = requestedRecipe == null ? java.util.List.<net.minecraft.world.item.crafting.RecipeHolder<?>>of()
                    : player.level().recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.RECIPE, requestedRecipe)).stream().toList();
            if (requestedRecipe != null && CraftlinesConfig.DEBUG_RECIPE_TYPE_MAPPINGS.get())
                showRecipeDebug(player, payload, candidates);
            DimensionsNet network = player.containerMenu instanceof DimensionsNetMenu dimensionsMenu
                    ? DimensionsNet.getAllNetFromPlayer(player).stream()
                            .filter(net -> net.getUnifiedStorage() == dimensionsMenu.storage).findFirst().orElse(null)
                    : DimensionsNet.getPrimaryNetFromPlayer(player);
            if (network == null)
            {
                player.sendSystemMessage(Component.translatable("error.beyond_craftlines.network_required"));
                return;
            }
            int networkId = network.getId();
            var availableFamilies = DeviceBindingRegistry.availableFamilies(player.level().getServer(), networkId);
            var recipe = candidates.stream()
                    .filter(RecipePlanningService::supported)
                    .filter(holder -> "crafting".equals(RecipePlanningService.family(holder))
                            || availableFamilies.contains(RecipePlanningService.family(holder)))
                    .filter(holder -> RecipeOutputResolver.outputs(holder.value(), player.level())
                            .stream().anyMatch(output -> target.isSame(output.key())))
                    .findFirst().orElse(null);
            // The JEI category id is presentation metadata, not an execution capability. A single
            // server RecipeType may be split across multiple JEI subcategories whose ids cannot be
            // inferred generically. The recipe id, actual server family, network binding and selected
            // output above are the authoritative checks.
            if (requestedRecipe != null && recipe == null)
            {
                player.sendSystemMessage(Component.translatable(
                        "error.beyond_craftlines.invalid_order_recipe"));
                return;
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new CraftlineOrderMenu(id, inventory, networkId, target,
                            recipe == null ? null : recipe.id().identifier(),
                            requestedRecipe != null, availableFamilies),
                    Component.translatable("menu.beyond_craftlines.order")), buffer -> {
                        buffer.writeVarInt(networkId);
                        IStackKey.STREAM_CODEC.encode(buffer, target);
                        buffer.writeUtf(recipe == null ? "" : recipe.id().identifier().toString());
                        buffer.writeBoolean(requestedRecipe != null);
                        buffer.writeVarInt(availableFamilies.size());
                        availableFamilies.stream().sorted().forEach(buffer::writeUtf);
                        buffer.writeBoolean(false);
                        buffer.writeBoolean(false);
                        buffer.writeVarLong(1);
                        buffer.writeUtf("network", 16);
                    });
        });
    }

    private static void showRecipeDebug(ServerPlayer player, OpenOrderMenuPayload payload,
                                        java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> candidates)
    {
        String families = candidates.stream().map(RecipePlanningService::family)
                .filter(java.util.Objects::nonNull).filter(family -> !family.isBlank())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(", "));
        Component actual = families.isEmpty()
                ? Component.translatable("message.beyond_craftlines.debug_recipe_type_not_found")
                : Component.literal(families);
        player.sendSystemMessage(Component.translatable(
                "message.beyond_craftlines.debug_order_recipe", payload.recipeId(),
                payload.jeiRecipeType(), actual));
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
