package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
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
            var recipe = RecipePlanningService.visibleRecipes(player.level()).stream()
                    .filter(holder -> requestedRecipe == null || holder.id().identifier().equals(requestedRecipe))
                    .filter(holder -> "crafting".equals(RecipePlanningService.family(holder))
                            || availableFamilies.contains(RecipePlanningService.family(holder)))
                    .filter(holder -> RecipeOutputResolver.outputs(holder.value(), player.level())
                            .stream().anyMatch(output -> target.isSame(output.key())))
                    .sorted(java.util.Comparator
                            .comparingInt((net.minecraft.world.item.crafting.RecipeHolder<?> holder) ->
                                    "crafting".equals(RecipePlanningService.family(holder)) ? 0 : 1)
                            .thenComparing(holder -> holder.id().identifier().toString()))
                    .findFirst().orElse(null);
            if (recipe == null || requestedType != null
                    && !DeviceBindingRegistry.supportsJeiType(player.level().getServer(), networkId,
                    requestedType, RecipePlanningService.family(recipe)))
            {
                player.sendSystemMessage(Component.translatable(
                        "error.beyond_craftlines.invalid_order_recipe"));
                return;
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new CraftlineOrderMenu(id, inventory, networkId, target, recipe.id().identifier(), availableFamilies),
                    Component.translatable("menu.beyond_craftlines.order")), buffer -> {
                        buffer.writeVarInt(networkId);
                        IStackKey.STREAM_CODEC.encode(buffer, target);
                        buffer.writeUtf(recipe.id().identifier().toString());
                        buffer.writeVarInt(availableFamilies.size());
                        availableFamilies.stream().sorted().forEach(buffer::writeUtf);
                    });
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
