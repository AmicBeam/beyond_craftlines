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
import net.minecraft.resources.Identifier;
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
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "plan_proposal_upload"));
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
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                        "{} server proposal rejected player={} nonce={} page={}/{} token={} error={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        player.getGameProfile().name(), payload.nonce(), payload.header().pageIndex(),
                        payload.header().pageCount(),
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.token(payload.itemId()),
                        exception.toString(), exception);
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
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} server proposal page player={} nonce={} page={}/{} recipes={} ingredients={} count={} stockRevision={} recipeEpoch={} target={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                player.getGameProfile().name(), payload.nonce(), header.pageIndex(), header.pageCount(),
                payload.recipeChoices().size(), payload.ingredientChoices().size(), header.count(),
                header.stockRevision(), header.recipeEpoch(),
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
        if (header.count() < 1 || header.pageCount() < 1 || header.pageCount() > MAX_PAGES
                || header.pageIndex() < 0 || header.pageIndex() >= header.pageCount())
            throw new IllegalArgumentException("invalid proposal page");
        if (!menu.targetToken().equals(payload.itemId()) || !menu.canPlanTarget(target))
            throw new IllegalArgumentException("target is unavailable");
        UUID playerId = player.getUUID();
        long now = player.level().getGameTime();
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

        RecipeResolutionOverrides overrides = SubmitOrderPayload.overrides(
                assembly.recipes(), assembly.ingredients());
        long expiresAt = now + CACHE_TICKS;
        ValidatedClientPlanCache.put(playerId, new ValidatedClientPlanCache.Entry(payload.nonce(),
                menu.networkId(), target, header.count(), header.recipeEpoch(), expiresAt, overrides));
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} server proposal cached player={} nonce={} network={} recipes={} ingredients={} expiresAt={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                player.getGameProfile().name(), payload.nonce(), menu.networkId(),
                assembly.recipes().size(), assembly.ingredients().size(), expiresAt);
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
