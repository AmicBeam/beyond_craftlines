package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

public record PlanProposalUploadPayload(long nonce, String itemId, Header header,
                                        List<SubmitOrderPayload.RecipeChoice> recipeChoices,
                                        List<SubmitOrderPayload.IngredientChoice> ingredientChoices)
        implements CustomPacketPayload
{
    private static final int PAGE_SIZE = 256;
    private static final int MAX_PAGES = 64;
    private static final long CACHE_TICKS = 20 * 30;
    private static final Map<UUID, Assembly> ASSEMBLIES = new HashMap<>();
    public static final Type<PlanProposalUploadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "plan_proposal_upload"));
    public static final StreamCodec<ByteBuf, PlanProposalUploadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PlanProposalUploadPayload::nonce,
            ByteBufCodecs.stringUtf8(256), PlanProposalUploadPayload::itemId,
            Header.STREAM_CODEC, PlanProposalUploadPayload::header,
            ByteBufCodecs.collection(ArrayList::new, SubmitOrderPayload.RECIPE_CHOICE_CODEC, PAGE_SIZE),
            PlanProposalUploadPayload::recipeChoices,
            ByteBufCodecs.collection(ArrayList::new, SubmitOrderPayload.INGREDIENT_CHOICE_CODEC, PAGE_SIZE),
            PlanProposalUploadPayload::ingredientChoices,
            PlanProposalUploadPayload::new);

    public static void handle(PlanProposalUploadPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || !menu.canAccessNetwork(player)) return;
            try { accept(player, menu, payload); }
            catch (RuntimeException exception)
            {
                ASSEMBLIES.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, PlanPreviewPayload.failure(
                        payload.nonce(), payload.itemId(), exception.getMessage()));
            }
        });
    }

    private static void accept(ServerPlayer player, CraftlineOrderMenu menu, PlanProposalUploadPayload payload)
    {
        IStackKey<?> target = menu.initialTarget();
        Header header = payload.header();
        if (header.count() < 1 || header.pageCount() < 1 || header.pageCount() > MAX_PAGES
                || header.pageIndex() < 0 || header.pageIndex() >= header.pageCount())
            throw new IllegalArgumentException("invalid proposal page");
        if (!menu.targetToken().equals(payload.itemId()) || menu.recipeForResourceOutput(target) == null)
            throw new IllegalArgumentException("target is unavailable");
        UUID playerId = player.getUUID();
        long now = player.serverLevel().getGameTime();
        ASSEMBLIES.values().removeIf(existing -> existing.expiresAt() < now);
        Assembly assembly = ASSEMBLIES.get(playerId);
        if (header.pageIndex() == 0)
        {
            assembly = new Assembly(payload.nonce(), payload.itemId(), header, new ArrayList<>(), new ArrayList<>(),
                    new ProposalPageSequence(header.pageCount(), MAX_PAGES), now + 20 * 10);
            ASSEMBLIES.put(playerId, assembly);
        }
        if (assembly == null || !assembly.matches(payload))
            throw new IllegalArgumentException("invalid proposal page sequence");
        assembly.sequence().accept(header.pageIndex());
        assembly.recipes().addAll(payload.recipeChoices());
        assembly.ingredients().addAll(payload.ingredientChoices());
        if (!assembly.sequence().complete()) return;
        ASSEMBLIES.remove(playerId);

        var snapshot = PlanningSnapshotService.capture(menu.networkId());
        long recipeEpoch = PlanningSnapshotService.recipeEpoch(player.level(), menu.availableFamilies());
        // Inventory is intentionally not compared as a whole. The proposal fixes recipe/tag choices;
        // current component-aware materials are validated now and once more at submission.
        if (PlanningFreshness.evaluate(header.stockRevision(), snapshot.revision(), header.recipeEpoch(),
                recipeEpoch, true) == PlanningFreshness.Result.RECIPES_CHANGED)
        {
            ASSEMBLIES.remove(playerId);
            PacketDistributor.sendToPlayer(player, PlanPreviewPayload.stale(payload.nonce(), payload.itemId()));
            return;
        }
        RecipeResolutionOverrides overrides = SubmitOrderPayload.overrides(
                assembly.recipes(), assembly.ingredients());
        RecipePlan plan = RecipePlanningService.plan(player.serverLevel(), target, header.count(),
                snapshot, menu.availableFamilies(), overrides);
        if (!overrides.completelyResolves(plan))
            throw new IllegalArgumentException("client proposal is incomplete");
        if (!plan.craftable()) throw new IllegalStateException("missing: " + plan.missing());
        long expiresAt = now + CACHE_TICKS;
        ValidatedClientPlanCache.put(playerId, new ValidatedClientPlanCache.Entry(payload.nonce(),
                menu.networkId(), target, header.count(), header.recipeEpoch(), expiresAt, overrides));
        RecipePlan theoretical;
        try
        {
            theoretical = RecipePlanningService.plan(player.serverLevel(), target, header.count(),
                    Map.of(), menu.availableFamilies(), overrides);
        }
        catch (IllegalStateException exception)
        {
            // The empty-stock plan is used only for explanatory material totals.
            // It deliberately expands dependencies that the real, stock-aware plan can stop at,
            // so a large tag/recipe graph must not invalidate an otherwise valid order.
            if (!"recipe tree is too complex; planning budget exceeded".equals(exception.getMessage()))
                throw exception;
            theoretical = plan;
        }
        for (PlanPreviewPayload page : PlanPreviewPayload.from(payload.nonce(), plan, theoretical,
                player.serverLevel()))
            PacketDistributor.sendToPlayer(player, page);
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Header(long count, long stockRevision, long recipeEpoch, int pageIndex, int pageCount)
    {
        static final StreamCodec<ByteBuf, Header> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Header::count,
                ByteBufCodecs.VAR_LONG, Header::stockRevision,
                ByteBufCodecs.VAR_LONG, Header::recipeEpoch,
                ByteBufCodecs.VAR_INT, Header::pageIndex,
                ByteBufCodecs.VAR_INT, Header::pageCount,
                Header::new);
    }

    private record Assembly(long nonce, String target, Header header,
                            ArrayList<SubmitOrderPayload.RecipeChoice> recipes,
                            ArrayList<SubmitOrderPayload.IngredientChoice> ingredients,
                            ProposalPageSequence sequence, long expiresAt)
    {
        boolean matches(PlanProposalUploadPayload payload)
        {
            Header other = payload.header();
            return nonce == payload.nonce() && target.equals(payload.itemId())
                    && header.count() == other.count() && header.stockRevision() == other.stockRevision()
                    && header.recipeEpoch() == other.recipeEpoch() && header.pageCount() == other.pageCount();
        }
    }
}
