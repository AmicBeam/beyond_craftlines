package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderService;
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
            ByteBufCodecs.stringUtf8(256), IngredientChoice::item,
            IngredientChoice::new);
    public static final StreamCodec<ByteBuf, SubmitOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), SubmitOrderPayload::itemId,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::count,
            ByteBufCodecs.BOOL, SubmitOrderPayload::blockingMode,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::proposalNonce,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::stockRevision,
            ByteBufCodecs.VAR_LONG, SubmitOrderPayload::recipeEpoch,
            SubmitOrderPayload::new);

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
                player.getPersistentData().putLong(LAST_SUBMIT_TICK, now);
                long count = Math.max(1, payload.count());
                if (!menu.targetToken().equals(payload.itemId())
                        || menu.recipeForResourceOutput(menu.initialTarget()) == null)
                    throw new IllegalArgumentException("target is not available in this order menu");
                var validated = ValidatedClientPlanCache.consume(player.getUUID(), payload.proposalNonce(), now);
                if (validated == null || validated.networkId() != menu.networkId()
                        || !validated.target().isSame(menu.initialTarget()) || validated.count() != count
                        || validated.recipeEpoch() != payload.recipeEpoch())
                    throw new IllegalStateException("client plan is missing or expired; refresh the preview");
                var snapshot = com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService.capture(menu.networkId());
                long recipeEpoch = com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService.recipeEpoch(
                        player.level(), menu.availableFamilies());
                if (PlanningFreshness.recipesChanged(validated.recipeEpoch(), recipeEpoch))
                    throw new IllegalStateException("recipes changed; refresh the preview");
                var currentPlan = RecipePlanningService.plan(player.level(), menu.initialTarget(), count, snapshot,
                        menu.availableFamilies(), validated.overrides());
                if (!validated.overrides().completelyResolves(currentPlan))
                    throw new IllegalStateException("client plan is incomplete; refresh the preview");
                if (PlanningFreshness.evaluate(payload.stockRevision(), snapshot.revision(),
                        validated.recipeEpoch(), recipeEpoch, currentPlan.craftable())
                        == PlanningFreshness.Result.REQUIRED_MATERIALS_CHANGED)
                    throw new IllegalStateException("required ingredients changed: " + currentPlan.missing());
                var job = RecipeOrderService.enqueueValidated(player.level(), player.getUUID(), menu.networkId(),
                        currentPlan.target(), count, payload.blockingMode(), currentPlan);
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_craftlines.order_queued", job.id().toString()));
                OpenOrderStatusMenuPayload.open(player, menu.networkId());
            }
            catch (RuntimeException exception)
            {
                player.sendSystemMessage(Component.translatable("error.beyond_craftlines.order_failed", exception.getMessage()));
            }
        });
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
                        Identifier.parse(choice.item())))
                .toList();
        return new RecipeResolutionOverrides(recipes, ingredients);
    }

    public record RecipeChoice(String output, String recipe) {}
    public record IngredientChoice(String recipe, int slot, String item) {}
}
