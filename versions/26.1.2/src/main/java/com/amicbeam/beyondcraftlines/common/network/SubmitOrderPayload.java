package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderService;
import com.amicbeam.beyondcraftlines.common.runtime.OrderOutputDestination;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record SubmitOrderPayload(String itemId, long count, boolean blockingMode,
                                 String outputDestination,
                                 long proposalNonce, long stockRevision, long recipeEpoch) implements CustomPacketPayload
{
    private static final String LAST_SUBMIT_TICK = "beyond_craftlines_last_submit_tick";
    public static final Type<SubmitOrderPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "submit_order"));
    static final StreamCodec<ByteBuf, RecipeChoice> RECIPE_CHOICE_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), RecipeChoice::output,
            ByteBufCodecs.stringUtf8(256), RecipeChoice::recipe,
            RecipeChoice::new);
    static final StreamCodec<ByteBuf, IngredientChoice> INGREDIENT_CHOICE_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), IngredientChoice::recipe,
            ByteBufCodecs.VAR_INT, IngredientChoice::slot,
            ByteBufCodecs.stringUtf8(512), IngredientChoice::item,
            IngredientChoice::new);
    private static final StreamCodec<ByteBuf, Options> OPTIONS_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Options::blockingMode,
            ByteBufCodecs.stringUtf8(16), Options::outputDestination,
            Options::new);
    public static final StreamCodec<ByteBuf, SubmitOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), SubmitOrderPayload::itemId,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::count,
            OPTIONS_CODEC, payload -> new Options(payload.blockingMode(), payload.outputDestination()),
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::proposalNonce,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::stockRevision,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::recipeEpoch,
            (itemId, count, options, nonce, stockRevision, recipeEpoch) -> new SubmitOrderPayload(
                    itemId, count, options.blockingMode(), options.outputDestination(), nonce,
                    stockRevision, recipeEpoch));

    public static void handle(SubmitOrderPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || !menu.canAccessNetwork(player)) return;
            try
            {
                long now = player.level().getGameTime();
                long last = player.getPersistentData().getLongOr(LAST_SUBMIT_TICK, Long.MIN_VALUE);
                int cooldown = com.amicbeam.beyondcraftlines.CraftlinesConfig.ORDER_SUBMIT_COOLDOWN_TICKS.get();
                if (cooldown > 0 && last > 0 && now >= last && now - last < cooldown)
                    throw new IllegalStateException("orders are being submitted too quickly");
                long count = Math.max(1, payload.count());
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                        "{} server submit request player={} nonce={} network={} count={} stockRevision={} recipeEpoch={} menuToken={} payloadToken={} canPlan={} target={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        player.getGameProfile().name(), payload.proposalNonce(), menu.networkId(), count,
                        payload.stockRevision(), payload.recipeEpoch(),
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.token(menu.targetToken()),
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.token(payload.itemId()),
                        menu.canPlanTarget(menu.initialTarget()),
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(menu.initialTarget()));
                if (!menu.targetToken().equals(payload.itemId())
                        || !menu.canPlanTarget(menu.initialTarget()))
                    throw new IllegalArgumentException("target is not available in this order menu");
                var validated = ValidatedClientPlanCache.consume(player.getUUID(), payload.proposalNonce(), now);
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                        "{} server submit cache nonce={} hit={} cachedNetwork={} cachedCount={} cachedEpoch={} targetMatch={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        payload.proposalNonce(), validated != null,
                        validated == null ? -1 : validated.networkId(), validated == null ? -1 : validated.count(),
                        validated == null ? -1 : validated.recipeEpoch(), validated != null
                                && com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                                .exact(validated.target(), menu.initialTarget()));
                if (validated == null || validated.networkId() != menu.networkId()
                        || !com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                        .exact(validated.target(), menu.initialTarget()) || validated.count() != count
                        || validated.recipeEpoch() != payload.recipeEpoch())
                    throw new IllegalStateException("client plan is missing or expired; refresh the preview");
                var snapshot = com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService
                        .capture(menu.networkId());
                var currentPlan = RecipePlanningService.validateFixed(player.level(), menu.initialTarget(),
                        count, snapshot, menu.availableFamilies(), validated.overrides());
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                        "{} server submit fixed-plan steps={} missing={} reserved={} completeOverrides={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        currentPlan.steps().size(), currentPlan.missing().size(), currentPlan.reserved().size(),
                        validated.overrides().completelyResolves(currentPlan));
                if (!validated.overrides().completelyResolves(currentPlan))
                    throw new IllegalArgumentException("client plan is incomplete");
                if (!currentPlan.craftable())
                    throw new IllegalStateException("missing: " + currentPlan.missing());
                OrderOutputDestination outputDestination = OrderOutputDestination.byId(payload.outputDestination());
                if (outputDestination == OrderOutputDestination.INVENTORY
                        && !(currentPlan.targetKey() instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey))
                    throw new IllegalArgumentException("inventory delivery is unsupported for this resource");
                var job = RecipeOrderService.enqueueValidated(player.level(), player.getUUID(), menu.networkId(),
                        currentPlan.target(), count, payload.blockingMode(),
                        outputDestination, currentPlan);
                player.getPersistentData().putLong(LAST_SUBMIT_TICK, now);
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_craftlines.order_queued", job.id().toString()));
                try { OpenOrderStatusMenuPayload.open(player, menu.networkId(), job); }
                catch (RuntimeException exception)
                {
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                            "{} server order queued but status menu failed player={} order={} error={}",
                            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                            player.getGameProfile().name(), job.id(), exception.toString(), exception);
                }
            }
            catch (RuntimeException exception)
            {
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                        "{} server submit rejected player={} nonce={} target={} error={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        player.getGameProfile().name(), payload.proposalNonce(),
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(menu.initialTarget()),
                        exception.toString(), exception);
                player.sendSystemMessage(localizedFailure(exception.getMessage()));
            }
        });
    }

    private static Component localizedFailure(String error)
    {
        if ("orders are being submitted too quickly".equals(error))
            return Component.translatable("error.beyond_craftlines.order_submit_cooldown");
        if ("too many active recipe orders".equals(error))
            return Component.translatable("error.beyond_craftlines.order_limit");
        if ("network unavailable".equals(error) || "network not found".equals(error))
            return Component.translatable("error.beyond_craftlines.planning_network_unavailable");
        if ("target is not available in this order menu".equals(error))
            return Component.translatable("error.beyond_craftlines.planning_target_unavailable");
        if (error != null && (error.contains("refresh the preview") || error.equals("recipes changed")))
            return Component.translatable("error.beyond_craftlines.planning_stale");
        if (error != null && (error.startsWith("missing:")
                || error.startsWith("required ingredients changed:")
                || error.startsWith("required resource changed:")))
            return Component.translatable("error.beyond_craftlines.planning_materials_changed");
        if (error != null && error.startsWith("network has no room to return reserved resource:"))
            return Component.translatable("error.beyond_craftlines.order_network_capacity");
        if ("inventory delivery is unsupported for this resource".equals(error))
            return Component.translatable("error.beyond_craftlines.order_inventory_unsupported");
        if ("client plan is incomplete".equals(error)
                || "validated plan does not match the order".equals(error))
            return Component.translatable("error.beyond_craftlines.planning_protocol_invalid");
        if ("server recipe index is still building".equals(error))
            return Component.translatable("error.beyond_craftlines.server_recipe_indexing");
        return Component.translatable("error.beyond_craftlines.order_failed_generic");
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    static RecipeResolutionOverrides overrides(List<RecipeChoice> recipeChoices,
                                                List<IngredientChoice> ingredientChoices)
    {
        List<RecipeResolutionOverrides.RecipeChoice> recipes = recipeChoices.stream()
                .map(choice -> new RecipeResolutionOverrides.RecipeChoice(
                        choice.output(), Identifier.parse(choice.recipe())))
                .toList();
        List<RecipeResolutionOverrides.IngredientChoice> ingredients = ingredientChoices.stream()
                .map(choice -> new RecipeResolutionOverrides.IngredientChoice(
                        Identifier.parse(choice.recipe()), choice.slot(),
                        choice.item()))
                .toList();
        return new RecipeResolutionOverrides(recipes, ingredients);
    }

    public record RecipeChoice(String output, String recipe) {}
    public record IngredientChoice(String recipe, int slot, String item) {}
    private record Options(boolean blockingMode, String outputDestination) {}
}
