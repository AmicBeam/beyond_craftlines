package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;

public final class CraftlineOrderMenu extends AbstractContainerMenu
{
    private final Player player;
    private final int networkId;
    private final IStackKey<?> initialTarget;
    private final ResourceLocation initialRecipe;
    private final boolean initialRecipePinned;
    private final Set<String> availableFamilies;
    private final RecipeHolder<?> initialRecipeHolder;
    private final BlockPos dashboardPosition;
    private final boolean initialBlockingMode;
    private final long initialDashboardDesired;
    private final String initialDashboardStockMode;
    private String initialError;

    public CraftlineOrderMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readVarInt(), IStackKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) data),
                optionalId(data.readUtf()), data.readBoolean(), readFamilies(data),
                data.readBoolean() ? data.readBlockPos() : null, data.readBoolean(),
                data.readVarLong(), data.readUtf(16));
        this.initialError = data.readableBytes() > 0 ? data.readUtf(512) : "";
    }

    public CraftlineOrderMenu(int id, Inventory inventory, int networkId, IStackKey<?> initialTarget,
                              ResourceLocation initialRecipe, boolean initialRecipePinned,
                              Set<String> availableFamilies)
    { this(id, inventory, networkId, initialTarget, initialRecipe, initialRecipePinned,
            availableFamilies, null, false, 1, "network"); }

    public CraftlineOrderMenu(int id, Inventory inventory, int networkId, IStackKey<?> initialTarget,
                              ResourceLocation initialRecipe, boolean initialRecipePinned,
                              Set<String> availableFamilies, BlockPos dashboardPosition,
                              boolean initialBlockingMode, long initialDashboardDesired,
                              String initialDashboardStockMode)
    {
        super(CraftlinesMenus.ORDER.get(), id);
        this.player = inventory.player;
        this.networkId = networkId;
        this.initialTarget = initialTarget;
        this.initialRecipe = initialRecipe;
        this.initialRecipePinned = initialRecipePinned;
        this.availableFamilies = Set.copyOf(availableFamilies);
        this.dashboardPosition = dashboardPosition == null ? null : dashboardPosition.immutable();
        this.initialBlockingMode = initialBlockingMode;
        this.initialDashboardDesired = Math.max(1, initialDashboardDesired);
        this.initialDashboardStockMode = initialDashboardStockMode == null ? "network" : initialDashboardStockMode;
        this.initialError = "";
        var level = player.level();
        this.initialRecipeHolder = initialRecipe == null ? null
                : findDisplayRecipe(level, initialRecipe);
    }

    public int networkId() { return networkId; }
    public IStackKey<?> initialTarget() { return initialTarget; }
    public String targetToken()
    { return com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(initialTarget); }
    public ResourceLocation initialRecipe() { return initialRecipe; }
    public boolean initialRecipePinned() { return initialRecipePinned; }
    public RecipeHolder<?> initialRecipeHolder() { return initialRecipeHolder; }
    public Set<String> availableFamilies() { return availableFamilies; }
    public boolean dashboardConfiguration() { return dashboardPosition != null; }
    public BlockPos dashboardPosition() { return dashboardPosition; }
    public boolean initialBlockingMode() { return initialBlockingMode; }
    public long initialDashboardDesired() { return initialDashboardDesired; }
    public String initialDashboardStockMode() { return initialDashboardStockMode; }
    public String initialError() { return initialError; }
    public List<RecipeHolder<?>> recipes()
    { return player.level().isClientSide() ? clientRecipes(com.amicbeam.beyondcraftlines.common.crafting
            .ClientRecipeLookupIndex.recipeIds()) : available(displayRecipes(player.level())); }
    public RecipeHolder<?> recipeForOutput(ResourceLocation output)
    { return recipesForOutput(output).stream().findFirst().orElse(null); }
    public List<RecipeHolder<?>> recipesForOutput(ResourceLocation output)
    {
        if (player.level().isClientSide()) return clientRecipes(com.amicbeam.beyondcraftlines.common.crafting
                .ClientRecipeLookupIndex.recipeIdsForItem(output.toString()));
        return recipes().stream().filter(holder -> RecipeOutputResolver.outputs(
                holder.value(), player.level().registryAccess()).stream().anyMatch(value ->
                value.key() instanceof ItemStackKey item && net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(item.getSource()).equals(output))).toList();
    }
    public List<RecipeHolder<?>> recipesForResourceOutput(IStackKey<?> output)
    {
        if (player.level().isClientSide()) return clientRecipes(com.amicbeam.beyondcraftlines.common.crafting
                .ClientRecipeLookupIndex.recipeIdsForOutput(output));
        return recipes().stream().filter(holder -> RecipeOutputResolver.outputs(
                holder.value(), player.level().registryAccess()).stream().anyMatch(value ->
                com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch.exact(output, value.key()))).toList();
    }
    public RecipeHolder<?> recipeForResourceOutput(IStackKey<?> output)
    { return recipesForResourceOutput(output).stream().findFirst().orElse(null); }
    public boolean canPlanTarget(IStackKey<?> output)
    {
        return initialRecipePinned && initialRecipe != null
                && initialRecipeHolder != null && RecipeOutputResolver.outputs(
                        initialRecipeHolder.value(), player.level().registryAccess()).stream()
                .anyMatch(value -> com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry
                        .outputMatches(initialRecipeHolder.value(), initialRecipeHolder.id().toString(),
                                output, value.key())) || recipeForResourceOutput(output) != null;
    }
    public RecipeHolder<?> recipe(ResourceLocation id)
    {
        RecipeHolder<?> holder = findDisplayRecipe(player.level(), id);
        return holder != null && available(holder) ? holder : null;
    }
    public ResourceLocation itemOutputForToken(String token)
    {
        if (player.level().isClientSide())
        {
            String item = com.amicbeam.beyondcraftlines.common.crafting.ClientRecipeLookupIndex
                    .itemForOutputToken(token);
            return item == null ? null : ResourceLocation.tryParse(item);
        }
        return recipes().stream()
                .flatMap(holder -> RecipeOutputResolver.outputs(holder.value(), player.level().registryAccess()).stream())
                .filter(value -> token.equals(com.amicbeam.beyondcraftlines.common.crafting
                        .RecipeResourceResolver.resolutionKey(value.key())) || token.equals(
                        com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(value.key())))
                .filter(value -> value.key() instanceof ItemStackKey).map(value ->
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(((ItemStackKey) value.key()).getSource())).findFirst().orElse(null);
    }
    public boolean recipeProduces(ResourceLocation recipe, String token)
    {
        if (player.level().isClientSide()) return com.amicbeam.beyondcraftlines.common.crafting
                .ClientRecipeLookupIndex.recipeProduces(recipe.toString(), token);
        RecipeHolder<?> holder = findDisplayRecipe(player.level(), recipe);
        return holder != null && RecipeOutputResolver.outputs(holder.value(), player.level().registryAccess())
                .stream().anyMatch(value -> token.equals(com.amicbeam.beyondcraftlines.common.crafting
                        .RecipeResourceResolver.sortKey(value.key())));
    }

    private List<RecipeHolder<?>> available(List<RecipeHolder<?>> holders)
    { return holders.stream().filter(this::available).toList(); }

    private List<RecipeHolder<?>> clientRecipes(List<String> ids)
    {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (String value : ids)
        {
            ResourceLocation id = ResourceLocation.tryParse(value);
            RecipeHolder<?> holder = id == null ? null : findDisplayRecipe(player.level(), id);
            if (holder != null && available(holder)) result.add(holder);
        }
        return List.copyOf(result);
    }

    private static List<RecipeHolder<?>> mergeRecipes(List<RecipeHolder<?>> first, List<RecipeHolder<?>> second)
    {
        LinkedHashMap<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        first.forEach(holder -> merged.put(holder.id(), holder));
        second.forEach(holder -> merged.putIfAbsent(holder.id(), holder));
        return merged.values().stream().sorted(java.util.Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    private boolean available(RecipeHolder<?> holder)
    { return RecipeIndexVisibility.includes(RecipePlanningService.family(holder), availableFamilies); }

    public boolean recipeIndexComplete()
    { return !player.level().isClientSide() || com.amicbeam.beyondcraftlines.common.crafting.ClientRecipeLookupIndex.ready(); }
    public int indexedRecipeCandidates()
    { return player.level().isClientSide() ? com.amicbeam.beyondcraftlines.client.ClientPlanningCatalogWarmup
            .handle().completedRecipes() : 0; }
    public int totalRecipeCandidates()
    { return player.level().isClientSide() ? com.amicbeam.beyondcraftlines.client.ClientPlanningCatalogWarmup
            .handle().totalRecipes() : 0; }

    /** Stable native base; JEI-only recipes are already keyed and cached separately by the planning catalog. */
    private static List<RecipeHolder<?>> baseClientRecipes(net.minecraft.world.level.Level level)
    {
        // Output resolution is performed lazily by the query/capture that actually needs it.
        return RecipePlanningService.allRecipes(level).stream()
                .filter(RecipePlanningService::supported)
                .filter(holder -> com.amicbeam.beyondcraftlines.common.crafting
                        .VanillaProvisionerRecipeTypes.isPotentialNetworkExecutable(
                                RecipePlanningService.family(holder))).toList();
    }

    private static List<RecipeHolder<?>> displayRecipes(net.minecraft.world.level.Level level)
    {
        return mergeRecipes(baseClientRecipes(level), com.amicbeam.beyondcraftlines.common.crafting
                .VirtualProvisionerRecipeRegistry.recipes());
    }

    private static RecipeHolder<?> findDisplayRecipe(net.minecraft.world.level.Level level, ResourceLocation id)
    {
        return level.getRecipeManager().byKey(id).filter(holder ->
                        com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes
                                .isPotentialNetworkExecutable(RecipePlanningService.family(holder)))
                .or(() -> com.amicbeam.beyondcraftlines.common.crafting
                        .VirtualProvisionerRecipeRegistry.find(id)).orElse(null);
    }

    public boolean canAccessNetwork(Player player)
    {
        if (player != this.player) return false;
        if (player.level().isClientSide()) return true;
        if (networkId < 0) return true;
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        return network != null && (network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID()));
    }

    private static Set<String> readFamilies(FriendlyByteBuf data)
    {
        int count = Math.min(512, Math.max(0, data.readVarInt()));
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) families.add(data.readUtf(256));
        return Set.copyOf(families);
    }

    private static ResourceLocation optionalId(String value)
    { return value == null || value.isBlank() ? null : ResourceLocation.tryParse(value); }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player)
    {
        return canAccessNetwork(player) && (dashboardPosition == null
                || player.blockPosition().distSqr(dashboardPosition) <= 64
                && player.level().getBlockEntity(dashboardPosition)
                instanceof com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity);
    }
}
