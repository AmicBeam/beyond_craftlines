package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.block.CraftlineDashboardBlock;
import com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.dashboard.AutomaticOrderPolicy;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardRecipeConfig;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardRedstoneMode;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardStockMode;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.menu.DashboardConfigMenu;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.UUID;

import static com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage.encode;

public final class CraftlineDashboardBlockEntity extends NetedBlockEntity
{
    private IStackKey<?> target = ItemStackKey.EMPTY;
    private long desiredAmount = 1;
    private DashboardStockMode stockMode = DashboardStockMode.NETWORK;
    private DashboardRedstoneMode redstoneMode = DashboardRedstoneMode.IGNORE;
    private DashboardRecipeConfig recipe = DashboardRecipeConfig.EMPTY;
    private UUID owner = new UUID(0, 0);
    private UUID activeOrder;
    private long activeOrderDelivered;
    private int checkTicks;
    private boolean wasPowered;
    private boolean pulsePending;
    private long lastObserved;
    private String lastError = "";

    public CraftlineDashboardBlockEntity(BlockPos position, BlockState state)
    { super(CraftlinesBlockEntities.CRAFTLINE_DASHBOARD.get(), position, state); }

    public IStackKey<?> target() { return target; }
    public long desiredAmount() { return desiredAmount; }
    public DashboardStockMode stockMode() { return stockMode; }
    public DashboardRedstoneMode redstoneMode() { return redstoneMode; }
    public DashboardRecipeConfig recipe() { return recipe; }
    public UUID activeOrder() { return activeOrder; }
    public long lastObserved() { return lastObserved; }
    public String lastError() { return lastError; }
    public boolean isActiveDashboard()
    { return getNetId() >= 0 && !target.isEmpty() && recipe.configured(); }
    public void setOwner(UUID value) { if (value != null) { owner = value; syncChanged(); } }

    public boolean configure(ServerPlayer player, IStackKey<?> nextTarget, long nextDesired,
                             DashboardStockMode nextStockMode, DashboardRedstoneMode nextRedstoneMode)
    {
        if (!mayConfigure(player) || nextDesired < 1) return false;
        if (nextTarget == null) nextTarget = ItemStackKey.EMPTY;
        boolean targetChanged = target.isEmpty() != nextTarget.isEmpty()
                || !target.isEmpty() && !com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                .exact(target, nextTarget);
        target = nextTarget;
        desiredAmount = nextDesired;
        stockMode = nextStockMode == null ? DashboardStockMode.NETWORK : nextStockMode;
        redstoneMode = nextRedstoneMode == null ? DashboardRedstoneMode.IGNORE : nextRedstoneMode;
        owner = player.getUUID();
        if (targetChanged) recipe = DashboardRecipeConfig.EMPTY;
        lastError = "";
        CraftlineDashboardIndex.refresh(this);
        syncChanged();
        return true;
    }

    public boolean saveRecipe(ServerPlayer player, DashboardRecipeConfig value)
    {
        int limit = Math.min(65_536, CraftlinesConfig.MAX_DASHBOARD_RECIPE_BYTES.get());
        if (!mayConfigure(player) || value == null || value.choiceCount()
                > CraftlinesConfig.MAX_DASHBOARD_RECIPE_CHOICES.get() || value.estimatedBytes() > limit)
            return false;
        recipe = value;
        owner = player.getUUID();
        lastError = "";
        CraftlineDashboardIndex.refresh(this);
        syncChanged();
        return true;
    }

    public boolean mayConfigure(ServerPlayer player)
    {
        if (player == null || player.blockPosition().distSqr(worldPosition) > 64) return false;
        DimensionsNet network = getNet();
        return network != null && (network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID()));
    }

    public void openConfiguration(ServerPlayer player)
    {
        if (!mayConfigure(player)) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new DashboardConfigMenu(id, inventory, worldPosition, this),
                Component.translatable("menu.beyond_craftlines.dashboard")), buffer ->
                DashboardConfigMenu.write(buffer, worldPosition, this));
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  CraftlineDashboardBlockEntity dashboard)
    {
        if (!(level instanceof ServerLevel serverLevel)) return;
        boolean powered = level.hasNeighborSignal(position);
        boolean rising = powered && !dashboard.wasPowered;
        dashboard.wasPowered = powered;
        if (rising) dashboard.pulsePending = true;
        if (++dashboard.checkTicks < CraftlinesConfig.DASHBOARD_CHECK_INTERVAL_TICKS.get()) return;
        dashboard.checkTicks = 0;
        boolean pulse = dashboard.pulsePending;
        dashboard.pulsePending = false;
        if (!dashboard.redstoneMode.allows(powered, pulse) || dashboard.target.isEmpty()
                || !dashboard.recipe.configured()) return;
        dashboard.checkAndOrder(serverLevel, state);
    }

    private void checkAndOrder(ServerLevel level, BlockState state)
    {
        DimensionsNet network = getNet();
        if (network == null) { recordError("network unavailable"); return; }
        RecipeOrderSavedData orders = RecipeOrderSavedData.get(level.getServer());
        if (activeOrder != null)
        {
            RecipeOrderJob existing = orders.get(activeOrder);
            if (existing != null && !terminal(existing.status())) return;
            activeOrder = null;
            activeOrderDelivered = 0;
            syncChanged();
        }
        int activeAutomatic = (int) orders.active().stream().filter(job -> job.networkId() == getNetId()
                && job.origin() == OrderOrigin.AUTOMATIC).count();
        if (!AutomaticOrderPolicy.canCreate(activeAutomatic,
                CraftlinesConfig.MAX_ACTIVE_AUTOMATIC_ORDERS_PER_NETWORK.get())) return;

        BlockPos supportPosition = CraftlineDashboardBlock.supportPos(worldPosition, state);
        net.minecraft.core.Direction supportSide = CraftlineDashboardBlock.supportSide(state);
        if (stockMode == DashboardStockMode.CONTAINER
                && !BoundMachineAutomation.supportsResource(
                level, supportPosition, supportSide, target))
        {
            updateObserved(0);
            recordError("dashboard_container_unavailable");
            return;
        }

        long stored = stockMode == DashboardStockMode.NETWORK
                ? networkAmount(network) : BoundMachineAutomation.countPresent(
                level, supportPosition, supportSide, target);
        updateObserved(stored);
        long deficit = AutomaticOrderPolicy.deficit(desiredAmount, stored);
        if (deficit <= 0) { recordError(""); return; }
        if (stockMode == DashboardStockMode.CONTAINER)
        {
            long capacity = BoundMachineAutomation.insertCapacity(
                    level, supportPosition, supportSide, target, deficit);
            if (capacity <= 0)
            {
                recordError("dashboard_container_blocked");
                return;
            }
            long transferred = transferNetworkStock(
                    network, level, supportPosition, supportSide, deficit, capacity);
            if (transferred > 0)
            {
                stored = BoundMachineAutomation.countPresent(
                        level, supportPosition, supportSide, target);
                updateObserved(stored);
                deficit = AutomaticOrderPolicy.deficit(desiredAmount, stored);
                if (deficit <= 0) { recordError(""); return; }
                capacity = BoundMachineAutomation.insertCapacity(
                        level, supportPosition, supportSide, target, deficit);
                if (capacity <= 0)
                {
                    recordError("dashboard_container_blocked");
                    return;
                }
            }
            deficit = AutomaticOrderPolicy.transferable(deficit, capacity);
        }

        try
        {
            PlanningSnapshotService.Snapshot snapshot = PlanningSnapshotService.capture(getNetId());
            Set<String> families = DeviceBindingRegistry.availableFamilies(level.getServer(), getNetId());
            RecipePlan plan = largestCraftable(level, deficit, snapshot, families);
            if (plan == null || !plan.craftable() || plan.requested() <= 0) return;
            RecipeOrderJob job = RecipeOrderService.enqueueAutomaticValidated(level, owner, getNetId(),
                    plan.target(), plan.requested(), recipe.blockingMode(),
                    stockMode == DashboardStockMode.CONTAINER
                            ? OrderOutputDestination.CONTAINER : OrderOutputDestination.NETWORK,
                    plan);
            activeOrder = job.id();
            activeOrderDelivered = 0;
            recordError("");
        }
        catch (RuntimeException exception)
        { recordError(exception.getMessage()); }
    }

    RecipeOrderJob deliverCompletedOrder(DimensionsNet network, RecipeOrderJob job)
    {
        if (stockMode != DashboardStockMode.CONTAINER || activeOrder == null
                || !activeOrder.equals(job.id()) || !(level instanceof ServerLevel serverLevel))
            return job.with(RecipeOrderJob.Status.PAUSED, encode("waiting_dashboard_container"));
        BlockState state = getBlockState();
        BlockPos supportPosition = CraftlineDashboardBlock.supportPos(worldPosition, state);
        net.minecraft.core.Direction supportSide = CraftlineDashboardBlock.supportSide(state);
        if (!BoundMachineAutomation.supportsResource(serverLevel, supportPosition, supportSide, target))
        {
            recordError("dashboard_container_unavailable");
            return job.with(RecipeOrderJob.Status.PAUSED, encode("waiting_dashboard_container"));
        }

        long remaining = Math.max(0, job.requested() - activeOrderDelivered);
        if (remaining <= 0)
        {
            recordError("");
            return job.with(RecipeOrderJob.Status.COMPLETE, "");
        }
        long capacity = BoundMachineAutomation.insertCapacity(
                serverLevel, supportPosition, supportSide, target, remaining);
        if (capacity <= 0)
        {
            updateObserved(BoundMachineAutomation.countPresent(
                    serverLevel, supportPosition, supportSide, target));
            recordError("dashboard_container_blocked");
            return job.with(RecipeOrderJob.Status.PAUSED, encode("waiting_dashboard_container_space"));
        }

        long requested = Math.min(remaining, capacity);
        com.wintercogs.beyonddimensions.api.storage.key.KeyAmount taken =
                network.getUnifiedStorage().extract(target, requested, false, false);
        if (taken.isEmpty())
            return job.with(RecipeOrderJob.Status.PAUSED, encode("waiting_final_output"));
        long inserted = BoundMachineAutomation.insert(
                serverLevel, supportPosition, supportSide, taken.key(), taken.amount());
        long rejected = Math.max(0, taken.amount() - inserted);
        if (rejected > 0) network.getUnifiedStorage().insert(taken.key(), rejected, false);
        if (inserted > 0)
        {
            activeOrderDelivered = Math.min(job.requested(), activeOrderDelivered + inserted);
            updateObserved(BoundMachineAutomation.countPresent(
                    serverLevel, supportPosition, supportSide, target));
            syncChanged();
        }
        if (activeOrderDelivered >= job.requested())
        {
            recordError("");
            return job.with(RecipeOrderJob.Status.COMPLETE, "");
        }
        recordError("dashboard_container_blocked");
        return job.with(RecipeOrderJob.Status.PAUSED,
                encode(inserted > 0 ? "waiting_dashboard_container_space" : "waiting_dashboard_container"));
    }

    private long transferNetworkStock(DimensionsNet network, ServerLevel serverLevel,
                                      BlockPos supportPosition, net.minecraft.core.Direction supportSide,
                                      long deficit, long capacity)
    {
        long requested = AutomaticOrderPolicy.transferable(deficit, capacity);
        if (requested <= 0) return 0;
        com.wintercogs.beyonddimensions.api.storage.key.KeyAmount taken =
                network.getUnifiedStorage().extract(target, requested, false, false);
        if (taken.isEmpty()) return 0;
        long inserted = BoundMachineAutomation.insert(
                serverLevel, supportPosition, supportSide, taken.key(), taken.amount());
        long rejected = Math.max(0, taken.amount() - inserted);
        if (rejected > 0) network.getUnifiedStorage().insert(taken.key(), rejected, false);
        return inserted;
    }

    private RecipePlan largestCraftable(ServerLevel level, long deficit,
                                        PlanningSnapshotService.Snapshot snapshot, Set<String> families)
    {
        RecipePlan full = RecipePlanningService.validateFixed(
                level, target, deficit, snapshot, families, recipe.overrides());
        if (full.craftable() && recipe.overrides().completelyResolves(full)) return full;
        long low = 0, high = deficit;
        RecipePlan best = null;
        while (low < high)
        {
            long middle = low + (high - low + 1) / 2;
            RecipePlan candidate = RecipePlanningService.validateFixed(
                    level, target, middle, snapshot, families, recipe.overrides());
            if (candidate.craftable() && recipe.overrides().completelyResolves(candidate))
            { low = middle; best = candidate; }
            else high = middle - 1;
        }
        return low <= 0 ? null : best != null && best.requested() == low ? best
                : RecipePlanningService.validateFixed(level, target, low, snapshot, families, recipe.overrides());
    }

    private long networkAmount(DimensionsNet network)
    {
        long total = 0;
        for (var stored : network.getUnifiedStorage().getStorage())
            if (com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                    .exact(target, stored.key()))
                total = total > Long.MAX_VALUE - Math.max(0, stored.amount())
                        ? Long.MAX_VALUE : total + Math.max(0, stored.amount());
        return total;
    }

    private void recordError(String value)
    {
        String next = value == null ? "" : value;
        if (next.length() > 256) next = next.substring(0, 256);
        if (!next.equals(lastError)) { lastError = next; syncChanged(); }
    }

    private void updateObserved(long value)
    {
        value = Math.max(0, value);
        if (value != lastObserved) { lastObserved = value; syncChanged(); }
    }

    private static boolean terminal(RecipeOrderJob.Status status)
    { return status == RecipeOrderJob.Status.COMPLETE || status == RecipeOrderJob.Status.CANCELLED
            || status == RecipeOrderJob.Status.ERROR; }

    private void syncChanged()
    {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public void onLoad()
    {
        super.onLoad();
        if (level != null && !level.isClientSide()) CraftlineDashboardIndex.refresh(this);
    }

    @Override public void setRemoved()
    {
        CraftlineDashboardIndex.remove(this);
        super.setRemoved();
    }

    @Override public void onNetChange()
    {
        super.onNetChange();
        CraftlineDashboardIndex.refresh(this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        tag.putLong("desired", desiredAmount);
        tag.putString("stock_mode", stockMode.id());
        tag.putString("redstone_mode", redstoneMode.id());
        tag.putString("owner", owner.toString());
        if (activeOrder != null) tag.putUUID("active_order", activeOrder);
        tag.putLong("active_order_delivered", activeOrderDelivered);
        tag.putLong("last_observed", lastObserved);
        tag.putString("last_error", lastError);
        if (!target.isEmpty())
        {
            tag.putString("target_type", target.getTypeId().toString());
            tag.put("target", target.serializeNBT(registries));
        }
        if (recipe.configured()) tag.put("recipe", recipe.save());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        desiredAmount = Math.max(1, tag.getLong("desired"));
        stockMode = DashboardStockMode.byId(tag.getString("stock_mode"));
        redstoneMode = DashboardRedstoneMode.byId(tag.getString("redstone_mode"));
        try { owner = UUID.fromString(tag.getString("owner")); }
        catch (RuntimeException ignored) { owner = new UUID(0, 0); }
        activeOrder = tag.hasUUID("active_order") ? tag.getUUID("active_order") : null;
        activeOrderDelivered = Math.max(0, tag.getLong("active_order_delivered"));
        lastObserved = Math.max(0, tag.getLong("last_observed"));
        lastError = tag.getString("last_error");
        ResourceLocation type = ResourceLocation.tryParse(tag.getString("target_type"));
        target = ItemStackKey.EMPTY;
        if (type != null && tag.contains("target", Tag.TAG_COMPOUND))
            try { target = StackKeyRegistry.getType(type).deserializeNBT(tag.getCompound("target"), registries); }
            catch (RuntimeException | LinkageError ignored) {}
        recipe = tag.contains("recipe", Tag.TAG_COMPOUND)
                ? DashboardRecipeConfig.load(tag.getCompound("recipe")) : DashboardRecipeConfig.EMPTY;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("recipe");
        return tag;
    }
}
