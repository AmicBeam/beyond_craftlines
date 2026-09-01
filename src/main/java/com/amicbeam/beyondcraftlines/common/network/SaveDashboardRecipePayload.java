package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardRecipeConfig;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SaveDashboardRecipePayload(BlockPos position, long desired, String stockMode,
                                         long proposalNonce, long recipeEpoch,
                                         boolean blockingMode) implements CustomPacketPayload
{
    public static final Type<SaveDashboardRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, "save_dashboard_recipe"));
    public static final StreamCodec<ByteBuf, SaveDashboardRecipePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SaveDashboardRecipePayload::position,
            ByteBufCodecs.VAR_LONG, SaveDashboardRecipePayload::desired,
            ByteBufCodecs.stringUtf8(16), SaveDashboardRecipePayload::stockMode,
            ByteBufCodecs.VAR_LONG, SaveDashboardRecipePayload::proposalNonce,
            ByteBufCodecs.VAR_LONG, SaveDashboardRecipePayload::recipeEpoch,
            ByteBufCodecs.BOOL, SaveDashboardRecipePayload::blockingMode,
            SaveDashboardRecipePayload::new);

    public static void handle(SaveDashboardRecipePayload payload, IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || !menu.dashboardConfiguration() || !payload.position().equals(menu.dashboardPosition())
                    || !menu.canAccessNetwork(player)
                    || !(player.level().getBlockEntity(payload.position()) instanceof CraftlineDashboardBlockEntity dashboard)
                    || !dashboard.mayConfigure(player)) return;
            long now = player.serverLevel().getGameTime();
            var validated = ValidatedClientPlanCache.consume(player.getUUID(), payload.proposalNonce(), now);
            if (payload.desired() < 1 || validated == null || validated.networkId() != menu.networkId()
                    || validated.count() != payload.desired()
                    || validated.recipeEpoch() != payload.recipeEpoch()
                    || !com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                    .exact(validated.target(), dashboard.target())) return;
            long currentEpoch = PlanningSnapshotService.recipeEpoch(player.level(), menu.availableFamilies());
            if (PlanningFreshness.recipesChanged(validated.recipeEpoch(), currentEpoch)) return;
            var plan = RecipePlanningService.validateFixed(player.serverLevel(), dashboard.target(), payload.desired(),
                    PlanningSnapshotService.capture(menu.networkId()), menu.availableFamilies(), validated.overrides());
            if (!validated.overrides().completelyResolves(plan)) return;
            if (!dashboard.configure(player, dashboard.target(), payload.desired(),
                    com.amicbeam.beyondcraftlines.common.dashboard.DashboardStockMode.byId(payload.stockMode()),
                    dashboard.redstoneMode())) return;
            dashboard.saveRecipe(player, new DashboardRecipeConfig(
                    validated.overrides(), payload.blockingMode()));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
