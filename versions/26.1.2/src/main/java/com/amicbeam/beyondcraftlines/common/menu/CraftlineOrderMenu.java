package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalog;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

public final class CraftlineOrderMenu extends AbstractContainerMenu
{
    private static final Map<Object, RecipeIndex> RECIPE_INDEX_CACHE = new WeakHashMap<>();
    private final Player player;
    private final int networkId;
    private final IStackKey<?> initialTarget;
    private final Identifier initialRecipe;
    private final boolean initialRecipePinned;
    private final Set<String> availableFamilies;
    private final RecipeIndex recipeIndex;
    private final RecipeHolder<?> initialRecipeHolder;

    public CraftlineOrderMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readVarInt(), IStackKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) data),
                Identifier.parse(data.readUtf()), data.readBoolean(), readFamilies(data));
    }

    public CraftlineOrderMenu(int id, Inventory inventory, int networkId, IStackKey<?> initialTarget,
                              Identifier initialRecipe, boolean initialRecipePinned,
                              Set<String> availableFamilies)
    {
        super(CraftlinesMenus.ORDER.get(), id);
        this.player = inventory.player;
        this.networkId = networkId;
        this.initialTarget = initialTarget;
        this.initialRecipe = initialRecipe;
        this.initialRecipePinned = initialRecipePinned;
        this.availableFamilies = Set.copyOf(availableFamilies);
        var level = player.level();
        Object source = level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess() : RecipeCatalog.class;
        List<RecipeHolder<?>> candidates = RecipeCatalog.forLevel(level).stream()
                .sorted(java.util.Comparator.comparing(holder -> holder.id().identifier().toString())).toList();
        synchronized (RECIPE_INDEX_CACHE)
        {
            this.recipeIndex = RECIPE_INDEX_CACHE.computeIfAbsent(source, ignored ->
                    new RecipeIndex(candidates, level));
        }
        this.initialRecipeHolder = candidates.stream()
                .filter(holder -> holder.id().identifier().equals(initialRecipe)).findFirst().orElse(null);
    }

    public int networkId() { return networkId; }
    public IStackKey<?> initialTarget() { return initialTarget; }
    public String targetToken()
    { return com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(initialTarget); }
    public Identifier initialRecipe() { return initialRecipe; }
    public boolean initialRecipePinned() { return initialRecipePinned; }
    public RecipeHolder<?> initialRecipeHolder() { return initialRecipeHolder; }
    public Set<String> availableFamilies() { return availableFamilies; }
    public List<RecipeHolder<?>> recipes()
    { ensureRecipeIndexForServer(); return available(recipeIndex.recipes()); }
    public RecipeHolder<?> recipeForOutput(Identifier output)
    { return recipesForOutput(output).stream().findFirst().orElse(null); }
    public List<RecipeHolder<?>> recipesForOutput(Identifier output)
    { ensureRecipeIndexForServer(); return available(recipeIndex.recipesForOutput(output)); }
    public List<RecipeHolder<?>> recipesForResourceOutput(IStackKey<?> output)
    {
        ensureRecipeIndexForServer();
        return available(recipeIndex.recipesForResourceOutput(output));
    }
    public RecipeHolder<?> recipeForResourceOutput(IStackKey<?> output)
    { return recipesForResourceOutput(output).stream().findFirst().orElse(null); }
    public RecipeHolder<?> recipe(Identifier id)
    {
        ensureRecipeIndexForServer();
        RecipeHolder<?> holder = recipeIndex.recipe(id);
        return holder != null && available(holder) ? holder : null;
    }
    public Identifier itemOutputForToken(String token)
    { ensureRecipeIndexForServer(); return recipeIndex.itemOutputForToken(token); }
    public boolean recipeProduces(Identifier recipe, String token)
    {
        ensureRecipeIndexForServer();
        return recipe(recipe) != null && recipeIndex.recipeProduces(recipe, token);
    }

    private List<RecipeHolder<?>> available(List<RecipeHolder<?>> holders)
    { return holders.stream().filter(this::available).toList(); }

    private boolean available(RecipeHolder<?> holder)
    { return RecipeIndexVisibility.includes(RecipePlanningService.family(holder), availableFamilies); }

    public void advanceRecipeIndex(int recipeBudget, long timeBudgetNanos)
    { recipeIndex.advance(recipeBudget, timeBudgetNanos); }
    public boolean recipeIndexComplete() { return recipeIndex.complete(); }
    public int indexedRecipeCandidates() { return recipeIndex.completedCandidates(); }
    public int totalRecipeCandidates() { return recipeIndex.totalCandidates(); }

    @Override public void broadcastChanges()
    {
        super.broadcastChanges();
        if (!player.level().isClientSide() && !recipeIndex.complete())
            recipeIndex.advance(CraftlinesConfig.RECIPE_INDEX_MAX_PER_TICK.get(), Long.MAX_VALUE);
    }

    private void ensureRecipeIndexForServer()
    {
        if (!player.level().isClientSide())
            while (!recipeIndex.complete()) recipeIndex.advance(Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    public static void clearRecipeIndexCache()
    {
        synchronized (RECIPE_INDEX_CACHE) { RECIPE_INDEX_CACHE.clear(); }
    }

    private static final class RecipeIndex
    {
        private final List<RecipeHolder<?>> candidates;
        private final net.minecraft.world.level.Level level;
        private final List<RecipeHolder<?>> recipes = new ArrayList<>();
        private final Map<Identifier, RecipeHolder<?>> recipesById = new LinkedHashMap<>();
        private final Map<String, Identifier> itemOutputsByToken = new LinkedHashMap<>();
        private final Map<Identifier, Set<String>> outputTokensByRecipe = new LinkedHashMap<>();
        private final Map<Identifier, RecipeHolder<?>> recipeByOutput = new LinkedHashMap<>();
        private final Map<Identifier, List<RecipeHolder<?>>> recipesByOutput = new LinkedHashMap<>();
        private final Map<IStackKey<?>, List<RecipeHolder<?>>> recipesByResourceOutput = new LinkedHashMap<>();
        private int next;

        private RecipeIndex(List<RecipeHolder<?>> candidates, net.minecraft.world.level.Level level)
        {
            this.candidates = List.copyOf(candidates);
            this.level = level;
        }

        private synchronized void advance(int recipeBudget, long timeBudgetNanos)
        {
            if (recipeBudget < 1 || timeBudgetNanos < 1 || complete()) return;
            int end = (int) Math.min(candidates.size(), (long) next + recipeBudget);
            int minimum = Math.min(16, recipeBudget);
            int processed = 0;
            long started = System.nanoTime();
            while (next < end && (processed < minimum || System.nanoTime() - started < timeBudgetNanos))
            {
                RecipeHolder<?> holder = candidates.get(next++);
                if (RecipePlanningService.supported(holder)) add(holder);
                processed++;
            }
        }

        private void add(RecipeHolder<?> holder)
        {
            var outputs = RecipeOutputResolver.outputs(holder.value(), level);
            if (outputs.isEmpty()) return;
            Identifier recipeId = holder.id().identifier();
            recipes.add(holder);
            recipesById.putIfAbsent(recipeId, holder);
            for (var output : outputs)
            {
                String token = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                        .sortKey(output.key());
                outputTokensByRecipe.computeIfAbsent(recipeId, ignored -> new LinkedHashSet<>()).add(token);
                recipesByResourceOutput.computeIfAbsent(output.key(), ignored -> new ArrayList<>()).add(holder);
                if (output.key() instanceof ItemStackKey itemKey)
                {
                    Identifier outputId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(itemKey.getSource());
                    itemOutputsByToken.putIfAbsent(token, outputId);
                    recipeByOutput.putIfAbsent(outputId, holder);
                    recipesByOutput.computeIfAbsent(outputId, ignored -> new ArrayList<>()).add(holder);
                }
            }
        }

        private synchronized List<RecipeHolder<?>> recipes() { return List.copyOf(recipes); }
        private synchronized List<RecipeHolder<?>> recipesForOutput(Identifier output)
        { return List.copyOf(recipesByOutput.getOrDefault(output, List.of())); }
        private synchronized List<RecipeHolder<?>> recipesForResourceOutput(IStackKey<?> output)
        {
            for (var entry : recipesByResourceOutput.entrySet())
                if (output.isSame(entry.getKey())) return List.copyOf(entry.getValue());
            return List.of();
        }
        private synchronized RecipeHolder<?> recipe(Identifier id) { return recipesById.get(id); }
        private synchronized Identifier itemOutputForToken(String token) { return itemOutputsByToken.get(token); }
        private synchronized boolean recipeProduces(Identifier recipe, String token)
        { return outputTokensByRecipe.getOrDefault(recipe, Set.of()).contains(token); }
        private synchronized int completedCandidates() { return next; }
        private int totalCandidates() { return candidates.size(); }
        private synchronized boolean complete() { return next >= candidates.size(); }
    }

    public boolean canAccessNetwork(Player player)
    {
        if (player != this.player) return false;
        if (player.level().isClientSide()) return true;
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

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return canAccessNetwork(player); }
}
