package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/** Builds a deterministic, server-authoritative dependency tree from loaded recipes. */
public final class RecipePlanningService
{
    private static final Map<RecipeManager, List<RecipeHolder<?>>> VISIBLE_RECIPE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<RecipeManager, Set<String>> LOADED_FAMILY_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RecipePlanningService() {}

    public static List<RecipeHolder<?>> visibleRecipes(Level level)
    {
        return VISIBLE_RECIPE_CACHE.computeIfAbsent(level.getRecipeManager(), manager -> manager.getRecipes().stream()
                .filter(RecipePlanningService::supported)
                .filter(holder -> !RecipeOutputResolver.outputs(
                        holder.value(), level.registryAccess()).isEmpty())
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList());
    }

    public static void clearRecipeCache()
    {
        VISIBLE_RECIPE_CACHE.clear();
        LOADED_FAMILY_CACHE.clear();
        RecipeIngredientResolver.clearCache();
        PlanningSnapshotService.clearRecipeEpochCache();
        ClientRecipePlanner.clearCache();
        com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu.clearRecipeIndexCache();
    }

    public static Set<String> loadedFamilies(Level level)
    {
        RecipeManager manager = level.getRecipeManager();
        return LOADED_FAMILY_CACHE.computeIfAbsent(manager, ignored -> manager.getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    public static RecipePlan plan(ServerLevel level, int networkId, ResourceLocation target, long amount)
    {
        return plan(level, networkId, target, amount, RecipeResolutionOverrides.EMPTY);
    }

    public static RecipePlan plan(ServerLevel level, int networkId, ResourceLocation target, long amount,
                                  RecipeResolutionOverrides overrides)
    {
        PlanningSnapshotService.Snapshot stock = PlanningSnapshotService.capture(networkId);
        Set<String> availableFamilies = DeviceBindingRegistry.availableFamilies(level.getServer(), networkId);
        return plan(level, target, amount, stock, availableFamilies, overrides);
    }

    public static RecipePlan plan(ServerLevel level, ResourceLocation target, long amount,
                                  PlanningSnapshotService.Snapshot suppliedStock,
                                  Set<String> availableFamilies, RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        for (PlanningSnapshotService.ComponentEntry entry : suppliedStock.componentEntries())
            exact.merge(entry.key(), entry.amount(), SaturatingLongMath::add);
        return plan(level, key(target), amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides, ResolutionMode.SEARCH);
    }

    public static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                  PlanningSnapshotService.Snapshot suppliedStock,
                                  Set<String> availableFamilies, RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        for (PlanningSnapshotService.ComponentEntry entry : suppliedStock.componentEntries())
            exact.merge(entry.key(), entry.amount(), SaturatingLongMath::add);
        return plan(level, target, amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides, ResolutionMode.SEARCH);
    }

    /** Recomputes one fully selected client proposal without exploring alternative recipes or ingredients. */
    public static RecipePlan validateFixed(ServerLevel level, IStackKey<?> target, long amount,
                                           PlanningSnapshotService.Snapshot suppliedStock,
                                           Set<String> availableFamilies, RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        for (PlanningSnapshotService.ComponentEntry entry : suppliedStock.componentEntries())
            exact.merge(entry.key(), entry.amount(), SaturatingLongMath::add);
        return plan(level, target, amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides, ResolutionMode.FIXED);
    }

    /** Expands only recipes explicitly selected by the client and treats every unselected resource as a leaf. */
    public static RecipePlan planSelectedChain(ServerLevel level, IStackKey<?> target, long amount,
                                               Set<String> availableFamilies,
                                               RecipeResolutionOverrides overrides)
    {
        return plan(level, target, amount,
                new MatchingStock<>(IStackKey::getTypeId, Map.of()),
                availableFamilies, overrides, ResolutionMode.SELECTED_CHAIN);
    }

    public static RecipePlan plan(ServerLevel level, ResourceLocation target, long amount,
                                  Map<ResourceLocation, Long> suppliedStock, Set<String> availableFamilies,
                                  RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        suppliedStock.forEach((item, count) -> exact.put(
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(item))), count));
        return plan(level, key(target), amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides, ResolutionMode.SEARCH);
    }

    public static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                  Map<ResourceLocation, Long> suppliedStock, Set<String> availableFamilies,
                                  RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        suppliedStock.forEach((item, count) -> exact.put(key(item), count));
        return plan(level, target, amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides, ResolutionMode.SEARCH);
    }

    private static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                   MatchingStock<IStackKey<?>, ResourceLocation> stock,
                                   Set<String> availableFamilies, RecipeResolutionOverrides overrides,
                                   ResolutionMode mode)
    {

        // The requested amount is a manufacturing quantity, not a desired final stock level.
        // Existing target items are shown to the player but never satisfy this order.
        stock.consume(target.getTypeId(), target::isSame, Long.MAX_VALUE);

        Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : visibleRecipes(level))
        {
            String family = family(holder);
            if (!"crafting".equals(family) && !availableFamilies.contains(family)) continue;
            for (KeyAmount output : RecipeOutputResolver.outputs(holder.value(), level.registryAccess()))
                byOutput.computeIfAbsent(output.key(), ignored -> new ArrayList<>()).add(holder);
        }
        PlanningState state = new PlanningState(stock, new ArrayList<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>());
        int maxDepth = CraftlinesConfig.MAX_PLANNING_DEPTH.get();
        resolve(level, target, amount, null, byOutput, new HashSet<>(), state, overrides, mode, 0, maxDepth,
                new PlanningBudget(CraftlinesConfig.MAX_PLANNING_NODES.get(),
                        CraftlinesConfig.MAX_PLANNING_TIME_MILLIS.get() * 1_000_000L));
        // A manufacturing order always creates its requested target. Even if a custom key implementation
        // or a cyclic third-party recipe exposes an equivalent target as stock later in planning, it must
        // never become a reservation that the order extracts from the network.
        state.usedStock.entrySet().removeIf(entry -> target.isSame(entry.getKey()));
        return new RecipePlan(target, amount, state.steps, state.missing.entrySet().stream()
                .map(entry -> new RecipePlan.Material(entry.getKey(), entry.getValue())).toList(),
                state.usedStock.entrySet().stream()
                        .map(entry -> new RecipePlan.ReservedMaterial(entry.getKey(), entry.getValue())).toList());
    }

    private static void resolve(ServerLevel level, IStackKey<?> resource, long needed,
                                Ingredient requiredIngredient,
                                Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                Set<String> visiting, PlanningState state,
                                RecipeResolutionOverrides overrides, ResolutionMode mode, int depth, int maxDepth,
                                PlanningBudget budget)
    {
        if (mode == ResolutionMode.SEARCH) budget.checkTime();
        long used = state.stock.consume(resource.getTypeId(), key -> resource.isSame(key)
                        && (requiredIngredient == null || key instanceof ItemStackKey itemKey
                        && requiredIngredient.test(itemKey.getReadOnlyStack())), needed,
                (key, amount) -> state.usedStock.merge(key, amount, SaturatingLongMath::add));
        long remainder = needed - used;
        if (remainder == 0) return;
        if (mode == ResolutionMode.SELECTED_CHAIN && overrides.recipeFor(resource) == null)
        {
            state.missing.merge(resource, remainder, SaturatingLongMath::add);
            return;
        }
        if (depth >= maxDepth)
        {
            state.missing.merge(resource, remainder, SaturatingLongMath::add);
            return;
        }
        String resourceId = RecipeResourceResolver.sortKey(resource);
        if (!visiting.add(resourceId))
            throw new CyclicRecipePathException(resource);

        try
        {
            List<RecipeHolder<?>> candidates = recipesFor(byOutput, resource);
            if (requiredIngredient != null) candidates = candidates.stream().filter(holder ->
                    requiredIngredient.test(holder.value().getResultItem(level.registryAccess()))).toList();
            ResourceLocation selectedRecipe = overrides.recipeFor(resource);
            if (selectedRecipe != null)
            {
                candidates = candidates.stream().filter(holder -> holder.id().equals(selectedRecipe)).toList();
                if (candidates.isEmpty())
                    throw new IllegalArgumentException("selected recipe is unavailable for " + resourceId + ": " + selectedRecipe);
            }
            else if (mode == ResolutionMode.FIXED && !candidates.isEmpty())
                throw new IllegalArgumentException("client proposal is incomplete");
            if (candidates.isEmpty())
            {
                state.missing.merge(resource, remainder, SaturatingLongMath::add);
                return;
            }

            if (!PlanningBranches.recipesRequireBranches(candidates.size()))
            {
                PlanningState branch = state.copy();
                try
                {
                    resolveRecipe(level, resource, remainder, candidates.getFirst(), byOutput,
                            new HashSet<>(visiting), branch, overrides, mode, depth, maxDepth, budget);
                    state.replaceWith(branch);
                }
                catch (CyclicRecipePathException cycle)
                {
                    if (!resource.isSame(cycle.resource)) throw cycle;
                    state.missing.merge(resource, remainder, SaturatingLongMath::add);
                }
                return;
            }
            PlanningState best = null;
            ResourceLocation bestRecipe = null;
            for (RecipeHolder<?> holder : candidates)
            {
                if (mode == ResolutionMode.SEARCH) budget.enterBranch();
                PlanningState branch = state.copy();
                try
                {
                    resolveRecipe(level, resource, remainder, holder, byOutput, new HashSet<>(visiting), branch,
                            overrides, mode, depth, maxDepth, budget);
                }
                catch (CyclicRecipePathException cycle)
                {
                    if (!resource.isSame(cycle.resource)) throw cycle;
                    branch = state.copy();
                    branch.missing.merge(resource, remainder, SaturatingLongMath::add);
                }
                if (best == null || compare(branch, holder.id(), best, bestRecipe) < 0)
                {
                    best = branch;
                    bestRecipe = holder.id();
                }
            }
            state.replaceWith(Objects.requireNonNull(best));
        }
        finally { visiting.remove(resourceId); }
    }

    private static void resolveRecipe(ServerLevel level, IStackKey<?> outputKey, long remainder,
                                      RecipeHolder<?> holder,
                                      Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                      Set<String> visiting, PlanningState state,
                                      RecipeResolutionOverrides overrides, ResolutionMode mode, int depth, int maxDepth,
                                      PlanningBudget budget)
    {
        List<Integer> slots = new ArrayList<>();
        List<List<KeyAmount>> options = new ArrayList<>();
        List<RecipeResourceResolver.ResourceIngredient> recipeIngredients =
                RecipeResourceResolver.ingredientsForOutput(holder.value(), outputKey);
        for (RecipeResourceResolver.ResourceIngredient ingredient : recipeIngredients)
        {
            int currentIndex = ingredient.slot();
            List<KeyAmount> choices = ingredientChoices(holder.id(), currentIndex, ingredient, state.stock,
                    byOutput, overrides, mode, budget);
            if (choices.isEmpty())
                throw new IllegalArgumentException("ingredient cannot be enumerated for " + holder.id()
                        + " slot " + currentIndex);
            slots.add(currentIndex);
            options.add(choices);
        }

        if (!PlanningBranches.ingredientsRequireBranches(options))
        {
            resolveRecipeVariant(level, outputKey, remainder, holder, byOutput, visiting, state,
                    overrides, mode, depth, maxDepth, budget, options.stream().map(List::getFirst).toList());
            return;
        }

        PlanningState best = null;
        String bestSelectionKey = null;
        for (List<KeyAmount> variant : SingleSubstitutionVariants.from(
                options, (left, right) -> left.key().isSameTypeSameComponents(right.key()),
                budget::checkGeneratedVariants))
        {
            if (mode == ResolutionMode.SEARCH) budget.enterBranch();
            PlanningState branch = state.copy();
            resolveRecipeVariant(level, outputKey, remainder, holder, byOutput, visiting, branch,
                    overrides, mode, depth, maxDepth, budget, variant);
            String selectionKey = variant.stream().map(value -> RecipeResourceResolver.sortKey(value.key()))
                    .collect(java.util.stream.Collectors.joining("|"));
            int comparison = best == null ? -1 : compare(branch, holder.id(), best, holder.id());
            if (best == null || comparison < 0 || comparison == 0 && selectionKey.compareTo(bestSelectionKey) < 0)
            {
                best = branch;
                bestSelectionKey = selectionKey;
            }
            if (missingAmount(branch.missing) == 0) break;
        }
        state.replaceWith(Objects.requireNonNull(best));
    }

    private static void resolveRecipeVariant(ServerLevel level, IStackKey<?> outputKey, long remainder,
                                             RecipeHolder<?> holder,
                                             Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                             Set<String> visiting, PlanningState state,
                                             RecipeResolutionOverrides overrides, ResolutionMode mode,
                                             int depth, int maxDepth,
                                             PlanningBudget budget,
                                             List<KeyAmount> variant)
    {
        int dependencyStart = state.steps.size();
        KeyAmount result = RecipeOutputResolver.outputs(holder.value(), level.registryAccess()).stream()
                .filter(value -> outputKey.isSame(value.key())).findFirst().orElseThrow();
        long perCraft = Math.max(1, result.amount());
        long crafts = SaturatingLongMath.ceilDiv(remainder, perCraft);
        List<RecipePlan.Material> inputs = new ArrayList<>();
        List<PlanningDependencyBatcher.Entry<IStackKey<?>>> dependencyInputs = new ArrayList<>();
        List<PlanningDependencyBatcher.Entry<IStackKey<?>>> reusableDependencyInputs = new ArrayList<>();
        Map<IStackKey<?>, Ingredient> dependencyIngredients = new LinkedHashMap<>();
        List<RecipeResourceResolver.ResourceIngredient> recipeIngredients =
                RecipeResourceResolver.ingredientsForOutput(holder.value(), outputKey);
        List<RecipePlan.IngredientSelection> selections = new ArrayList<>();
        for (int i = 0; i < variant.size(); i++)
            if (variant.get(i).key() instanceof ItemStackKey itemKey)
                selections.add(new RecipePlan.IngredientSelection(recipeIngredients.get(i).slot(),
                        BuiltInRegistries.ITEM.getKey(itemKey.getSource())));
        boolean[] reusableSlots = SimulatedCrafting.reusableIngredientSlots(holder, level, selections);
        Map<Integer, KeyAmount> fluidProxies = SimulatedCrafting.bucketFluidInputs(holder, level, selections);
        for (int i = 0; i < variant.size(); i++)
        {
            RecipeResourceResolver.ResourceIngredient ingredient = recipeIngredients.get(i);
            KeyAmount choice = fluidProxies.getOrDefault(ingredient.slot(), variant.get(i));
            boolean reusable = ingredient.slot() < reusableSlots.length && reusableSlots[ingredient.slot()];
            long inputAmount = PlanningDependencyBatcher.inputAmount(reusable, choice.amount(), crafts);
            inputs.add(new RecipePlan.Material(choice.key(), inputAmount, ingredient.slot(),
                    ingredient.inputGroup()));
            (reusable ? reusableDependencyInputs : dependencyInputs)
                    .add(new PlanningDependencyBatcher.Entry<>(choice.key(), inputAmount));
            dependencyIngredients.putIfAbsent(choice.key(), fluidProxies.containsKey(ingredient.slot())
                    ? null : ingredient.itemIngredient());
        }
        for (var dependency : PlanningDependencyBatcher.aggregate(dependencyInputs).entrySet())
            resolve(level, dependency.getKey(), dependency.getValue(),
                    dependencyIngredients.get(dependency.getKey()), byOutput, visiting, state,
                    overrides, mode, depth + 1, maxDepth, budget);
        for (var dependency : PlanningDependencyBatcher.aggregate(reusableDependencyInputs).entrySet())
        {
            long additional = PlanningDependencyBatcher.additionalReusableAmount(
                    state.reusableRequirements, dependency.getKey(), dependency.getValue());
            if (additional > 0)
                resolve(level, dependency.getKey(), additional,
                        dependencyIngredients.get(dependency.getKey()), byOutput, visiting, state,
                        overrides, mode, depth + 1, maxDepth, budget);
        }
        List<Integer> dependencies = java.util.stream.IntStream.range(dependencyStart, state.steps.size())
                .boxed().toList();
        state.steps.add(new RecipePlan.Step(holder.id(), family(holder), outputKey,
                perCraft, crafts, inputs, selections, dependencies));
        long produced = SaturatingLongMath.multiply(perCraft, crafts);
        long surplus = produced > remainder ? produced - remainder : 0;
        if (surplus > 0) state.stock.add(outputKey, surplus);
    }

    private static void consumeLeaf(IStackKey<?> requested, long amount, PlanningState state)
    {
        long used = state.stock.consume(requested.getTypeId(), requested::isSame, amount,
                (key, consumed) -> state.usedStock.merge(key, consumed, SaturatingLongMath::add));
        if (used < amount) state.missing.merge(requested, amount - used, SaturatingLongMath::add);
    }

    private static List<KeyAmount> ingredientChoices(ResourceLocation recipe, int slot,
                                                     RecipeResourceResolver.ResourceIngredient ingredient,
                                                     MatchingStock<IStackKey<?>, ResourceLocation> stock,
                                                     Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                                     RecipeResolutionOverrides overrides,
                                                     ResolutionMode mode,
                                                     PlanningBudget budget)
    {
        ResourceLocation selected = overrides.ingredientFor(recipe, slot);
        if (selected != null)
        {
            for (KeyAmount choice : ingredient.candidates())
                if (choice.key() instanceof ItemStackKey item
                        && BuiltInRegistries.ITEM.getKey(item.getSource()).equals(selected)) return List.of(choice);
            throw new IllegalArgumentException("selected ingredient is invalid for " + recipe
                    + " slot " + slot + ": " + selected);
        }
        if (mode != ResolutionMode.SEARCH && ingredient.candidates().stream()
                .anyMatch(choice -> choice.key() instanceof ItemStackKey))
            throw new IllegalArgumentException("client proposal is incomplete");
        Comparator<KeyAmount> comparator = Comparator.<KeyAmount>comparingLong(value -> stock.available(
                        value.key().getTypeId(), value.key()::isSame)).reversed()
                .thenComparing(value -> !recipesFor(byOutput, value.key()).isEmpty() ? 0 : 1)
                .thenComparing(value -> RecipeResourceResolver.sortKey(value.key()));
        List<KeyAmount> choices = ingredient.candidates();
        if (mode == ResolutionMode.SEARCH)
            for (int i = 0; i < choices.size(); i++)
                if ((i & 255) == 0) budget.checkTime();
        LinkedHashMap<IStackKey<?>, KeyAmount> unique = new LinkedHashMap<>();
        choices.stream().sorted(comparator).forEach(choice -> unique.putIfAbsent(choice.key(), choice));
        return List.copyOf(unique.values());
    }

    private enum ResolutionMode { SEARCH, FIXED, SELECTED_CHAIN }


    private static final class CyclicRecipePathException extends RuntimeException
    {
        private final IStackKey<?> resource;
        private CyclicRecipePathException(IStackKey<?> resource) { this.resource = resource; }
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static List<RecipeHolder<?>> recipesFor(Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                                     IStackKey<?> resource)
    {
        for (var entry : byOutput.entrySet()) if (resource.isSame(entry.getKey())) return entry.getValue();
        return List.of();
    }

    private static int compare(PlanningState left, ResourceLocation leftRecipe,
                               PlanningState right, ResourceLocation rightRecipe)
    {
        return new PlanningCandidateRank(missingAmount(left.missing), left.steps.size(), leftRecipe.toString())
                .compareTo(new PlanningCandidateRank(
                        missingAmount(right.missing), right.steps.size(), rightRecipe.toString()));
    }

    private static long missingAmount(Map<?, Long> missing)
    {
        long total = 0;
        for (long amount : missing.values()) total = SaturatingLongMath.add(total, amount);
        return total;
    }

    private static ItemStackKey key(ResourceLocation item)
    { return new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(item))); }

    private static final class PlanningState
    {
        private MatchingStock<IStackKey<?>, ResourceLocation> stock;
        private List<RecipePlan.Step> steps;
        private Map<IStackKey<?>, Long> missing;
        private Map<IStackKey<?>, Long> usedStock;
        private Map<IStackKey<?>, Long> reusableRequirements;

        private PlanningState(MatchingStock<IStackKey<?>, ResourceLocation> stock, List<RecipePlan.Step> steps,
                              Map<IStackKey<?>, Long> missing, Map<IStackKey<?>, Long> usedStock,
                              Map<IStackKey<?>, Long> reusableRequirements)
        {
            this.stock = stock;
            this.steps = steps;
            this.missing = missing;
            this.usedStock = usedStock;
            this.reusableRequirements = reusableRequirements;
        }

        private PlanningState copy()
        { return new PlanningState(stock.copy(), new ArrayList<>(steps), new LinkedHashMap<>(missing),
                new LinkedHashMap<>(usedStock), new LinkedHashMap<>(reusableRequirements)); }

        private void replaceWith(PlanningState selected)
        {
            stock = selected.stock;
            steps = selected.steps;
            missing = selected.missing;
            usedStock = selected.usedStock;
            reusableRequirements = selected.reusableRequirements;
        }
    }

    public static boolean supported(RecipeHolder<?> holder)
    {
        var recipe = holder.value();
        // Recipe#isIncomplete treats an empty vanilla getIngredients() list as invalid.
        // Many third-party machine recipes intentionally keep that list empty and expose
        // their inputs through their own API, so only apply the vanilla check when the
        // recipe actually uses the vanilla ingredient list.
        if (!recipe.getIngredients().isEmpty() && recipe.isIncomplete()) return false;
        return !RecipeResourceResolver.ingredients(recipe).isEmpty();
    }

    public static String family(RecipeHolder<?> holder)
    {
        var recipe = holder.value();
        RecipeType<?> type = recipe.getType();
        String byType = family(type);
        if (byType != null) return byType;
        var recipeSerializer = recipe.getSerializer();
        ResourceLocation serializer = recipeSerializer == null ? null
                : BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipeSerializer);
        if (serializer != null) return serializer.toString();
        return type == null ? null : type.toString();
    }

    public static String family(RecipeType<?> type)
    {
        if (type == null) return null;
        if (type == RecipeType.CRAFTING) return "crafting";
        if (type == RecipeType.SMELTING) return "smelting";
        if (type == RecipeType.BLASTING) return "blasting";
        if (type == RecipeType.SMOKING) return "smoking";
        if (type == RecipeType.CAMPFIRE_COOKING) return "campfire_cooking";
        if (type == RecipeType.STONECUTTING) return "stonecutting";
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? null : id.toString();
    }
}
