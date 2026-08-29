package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.*;

/** Builds a deterministic, server-authoritative dependency tree from loaded recipes. */
public final class RecipePlanningService
{
    private static final Map<Object, List<RecipeHolder<?>>> VISIBLE_RECIPE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Set<String>> LOADED_FAMILY_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RecipePlanningService() {}

    public static List<RecipeHolder<?>> visibleRecipes(Level level)
    {
        Object catalog = level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess() : RecipeCatalog.class;
        List<RecipeHolder<?>> base = VISIBLE_RECIPE_CACHE.computeIfAbsent(catalog, ignored ->
                RecipeCatalog.forLevel(level).stream()
                .filter(RecipePlanningService::supported)
                .filter(holder -> !RecipeOutputResolver.outputs(
                        holder.value(), level).isEmpty())
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
                .toList());
        return java.util.stream.Stream.concat(base.stream(), VirtualProvisionerRecipeRegistry.recipes().stream()
                        .filter(RecipePlanningService::supported)
                        .filter(holder -> !RecipeOutputResolver.outputs(holder.value(), level).isEmpty()))
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString())).toList();
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
        Object catalog = level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess() : RecipeCatalog.class;
        Set<String> base = LOADED_FAMILY_CACHE.computeIfAbsent(catalog, ignored -> RecipeCatalog.forLevel(level)
                .stream().map(RecipePlanningService::family)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        java.util.HashSet<String> families = new java.util.HashSet<>(base);
        VirtualProvisionerRecipeRegistry.recipes().stream().map(RecipePlanningService::family)
                .filter(java.util.Objects::nonNull).forEach(families::add);
        return Set.copyOf(families);
    }

    public static RecipePlan plan(ServerLevel level, int networkId, Identifier target, long amount)
    {
        return plan(level, networkId, target, amount, RecipeResolutionOverrides.EMPTY);
    }

    public static RecipePlan plan(ServerLevel level, int networkId, Identifier target, long amount,
                                  RecipeResolutionOverrides overrides)
    {
        PlanningSnapshotService.Snapshot stock = PlanningSnapshotService.capture(networkId);
        Set<String> availableFamilies = DeviceBindingRegistry.availableFamilies(level.getServer(), networkId);
        return plan(level, target, amount, stock, availableFamilies, overrides);
    }

    public static RecipePlan plan(ServerLevel level, Identifier target, long amount,
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

    public static RecipePlan plan(ServerLevel level, Identifier target, long amount,
                                  Map<Identifier, Long> suppliedStock, Set<String> availableFamilies,
                                  RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        suppliedStock.forEach((item, count) -> exact.put(
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(item))), count));
        return plan(level, key(target), amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides, ResolutionMode.SEARCH);
    }

    public static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                  Map<Identifier, Long> suppliedStock, Set<String> availableFamilies,
                                  RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        suppliedStock.forEach((item, count) -> exact.put(key(item), count));
        return plan(level, target, amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides, ResolutionMode.SEARCH);
    }

    private static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                   MatchingStock<IStackKey<?>, Identifier> stock,
                                   Set<String> availableFamilies, RecipeResolutionOverrides overrides,
                                   ResolutionMode mode)
    {

        Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput = new LinkedHashMap<>();
        Iterable<RecipeHolder<?>> candidates;
        if (mode == ResolutionMode.SEARCH) candidates = visibleRecipes(level);
        else
        {
            List<RecipeHolder<?>> selected = new ArrayList<>();
            for (Identifier id : overrides.selectedRecipes())
            {
                RecipeHolder<?> holder = VirtualProvisionerRecipeRegistry.find(id).orElse(null);
                if (holder != null) selected.add(holder);
            }
            candidates = selected;
        }
        for (RecipeHolder<?> holder : candidates)
        {
            String family = family(holder);
            if (!"crafting".equals(family) && !availableFamilies.contains(family)) continue;
            for (KeyAmount output : RecipeOutputResolver.outputs(holder.value(), level))
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
        boolean targetIsSelfIncrementSeed = state.steps.stream().anyMatch(step ->
                step.selfIncrementSeed() > 0 && target.isSame(step.outputKey()));
        if (!targetIsSelfIncrementSeed)
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
        // A root order always manufactures its full amount, but its existing stock must remain
        // available to a self-increment child as the minimum seed.
        long used = depth == 0 ? 0 : state.stock.consume(resource.getTypeId(), key -> resource.isSame(key)
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
            throw new PlanningCycleBranch.Cycle();

        try
        {
            List<RecipeHolder<?>> candidates = recipesFor(byOutput, resource);
            if (requiredIngredient != null) candidates = candidates.stream().filter(holder ->
                    RecipeOutputResolver.outputs(holder.value(), level).stream().anyMatch(output ->
                            output.key() instanceof ItemStackKey item && requiredIngredient.test(item.getReadOnlyStack()))).toList();
            Identifier selectedRecipe = overrides.recipeFor(resource);
            if (selectedRecipe != null)
            {
                candidates = candidates.stream().filter(holder -> holder.id().identifier().equals(selectedRecipe)).toList();
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
                RecipeHolder<?> onlyCandidate = candidates.getFirst();
                PlanningState branch = PlanningCycleBranch.evaluate(state, () -> {
                    PlanningState attempted = state.copy();
                    resolveRecipe(level, resource, remainder, onlyCandidate, byOutput,
                            new HashSet<>(visiting), attempted, overrides, mode, depth, maxDepth, budget);
                    return attempted;
                }, baseline -> rejectCyclicCandidate(baseline, resource, remainder));
                state.replaceWith(branch);
                return;
            }
            PlanningState best = null;
            Identifier bestRecipe = null;
            for (RecipeHolder<?> holder : candidates)
            {
                if (mode == ResolutionMode.SEARCH) budget.enterBranch();
                PlanningState branch = PlanningCycleBranch.evaluate(state, () -> {
                    PlanningState attempted = state.copy();
                    resolveRecipe(level, resource, remainder, holder, byOutput, new HashSet<>(visiting), attempted,
                            overrides, mode, depth, maxDepth, budget);
                    return attempted;
                }, baseline -> rejectCyclicCandidate(baseline, resource, remainder));
                if (best == null || compare(branch, holder.id().identifier(), best, bestRecipe) < 0)
                {
                    best = branch;
                    bestRecipe = holder.id().identifier();
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
            List<KeyAmount> choices = ingredientChoices(holder.id().identifier(), currentIndex, ingredient, state.stock,
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
            int comparison = best == null ? -1 : compare(branch, holder.id().identifier(), best, holder.id().identifier());
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
        KeyAmount result = RecipeOutputResolver.outputs(holder.value(), level).stream()
                .filter(value -> outputKey.isSame(value.key())).findFirst().orElseThrow();
        long perCraft = Math.max(1, result.amount());
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
        long seedPerCraft = 0;
        long consumedSeedPerCraft = 0;
        for (int i = 0; i < variant.size(); i++)
        {
            RecipeResourceResolver.ResourceIngredient ingredient = recipeIngredients.get(i);
            KeyAmount choice = fluidProxies.getOrDefault(ingredient.slot(), variant.get(i));
            if (!outputKey.isSame(choice.key())) continue;
            seedPerCraft = SaturatingLongMath.add(seedPerCraft, choice.amount());
            if (!(ingredient.slot() < reusableSlots.length && reusableSlots[ingredient.slot()]))
                consumedSeedPerCraft = SaturatingLongMath.add(consumedSeedPerCraft, choice.amount());
        }
        SelfIncrementRecipe.Shape shape = SelfIncrementRecipe.analyze(
                perCraft, seedPerCraft, consumedSeedPerCraft, remainder);
        long crafts = shape.crafts();
        for (int i = 0; i < variant.size(); i++)
        {
            RecipeResourceResolver.ResourceIngredient ingredient = recipeIngredients.get(i);
            KeyAmount choice = fluidProxies.getOrDefault(ingredient.slot(), variant.get(i));
            boolean reusable = ingredient.slot() < reusableSlots.length && reusableSlots[ingredient.slot()];
            boolean selfInput = shape.selfIncrement() && outputKey.isSame(choice.key());
            long inputAmount = selfInput ? choice.amount()
                    : PlanningDependencyBatcher.inputAmount(reusable, choice.amount(), crafts);
            inputs.add(new RecipePlan.Material(choice.key(), inputAmount, ingredient.slot(),
                    ingredient.inputGroup()));
            (reusable ? reusableDependencyInputs : dependencyInputs)
                    .add(new PlanningDependencyBatcher.Entry<>(choice.key(), inputAmount));
            dependencyIngredients.putIfAbsent(choice.key(), fluidProxies.containsKey(ingredient.slot())
                    ? null : ingredient.itemIngredient());
        }
        for (var dependency : PlanningDependencyBatcher.aggregate(dependencyInputs).entrySet())
            if (shape.selfIncrement() && outputKey.isSame(dependency.getKey()))
                consumeLeaf(dependency.getKey(), dependency.getValue(), state);
            else resolve(level, dependency.getKey(), dependency.getValue(),
                        dependencyIngredients.get(dependency.getKey()), byOutput, visiting, state,
                        overrides, mode, depth + 1, maxDepth, budget);
        for (var dependency : PlanningDependencyBatcher.aggregate(reusableDependencyInputs).entrySet())
        {
            if (shape.selfIncrement() && outputKey.isSame(dependency.getKey()))
            {
                consumeLeaf(dependency.getKey(), dependency.getValue(), state);
                continue;
            }
            long additional = PlanningDependencyBatcher.additionalReusableAmount(
                    state.reusableRequirements, dependency.getKey(), dependency.getValue());
            if (additional > 0)
                resolve(level, dependency.getKey(), additional,
                        dependencyIngredients.get(dependency.getKey()), byOutput, visiting, state,
                        overrides, mode, depth + 1, maxDepth, budget);
        }
        List<Integer> dependencies = java.util.stream.IntStream.range(dependencyStart, state.steps.size())
                .boxed().toList();
        state.steps.add(new RecipePlan.Step(holder.id().identifier(), family(holder), outputKey,
                perCraft, crafts, inputs, selections, dependencies, shape.seed()));
        long produced = SaturatingLongMath.multiply(shape.netOutputPerCraft(), crafts);
        long surplus = produced > remainder ? produced - remainder : 0;
        if (surplus > 0) state.stock.add(outputKey, surplus);
    }

    private static void consumeLeaf(IStackKey<?> requested, long amount, PlanningState state)
    {
        long used = state.stock.consume(requested.getTypeId(), requested::isSame, amount,
                (key, consumed) -> state.usedStock.merge(key, consumed, SaturatingLongMath::add));
        if (used < amount) state.missing.merge(requested, amount - used, SaturatingLongMath::add);
    }

    private static List<KeyAmount> ingredientChoices(Identifier recipe, int slot,
                                                     RecipeResourceResolver.ResourceIngredient ingredient,
                                                     MatchingStock<IStackKey<?>, Identifier> stock,
                                                     Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                                     RecipeResolutionOverrides overrides,
                                                     ResolutionMode mode,
                                                     PlanningBudget budget)
    {
        Identifier selected = overrides.ingredientFor(recipe, slot);
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


    private static PlanningState rejectCyclicCandidate(PlanningState baseline, IStackKey<?> resource,
                                                        long remainder)
    {
        PlanningState rejected = baseline.copy();
        rejected.missing.merge(resource, remainder, SaturatingLongMath::add);
        return rejected;
    }

    private static List<RecipeHolder<?>> recipesFor(Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                                     IStackKey<?> resource)
    {
        for (var entry : byOutput.entrySet()) if (resource.isSame(entry.getKey())) return entry.getValue();
        return List.of();
    }

    private static int compare(PlanningState left, Identifier leftRecipe,
                               PlanningState right, Identifier rightRecipe)
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

    private static ItemStackKey key(Identifier item)
    { return new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(item))); }

    private static final class PlanningState
    {
        private MatchingStock<IStackKey<?>, Identifier> stock;
        private List<RecipePlan.Step> steps;
        private Map<IStackKey<?>, Long> missing;
        private Map<IStackKey<?>, Long> usedStock;
        private Map<IStackKey<?>, Long> reusableRequirements;

        private PlanningState(MatchingStock<IStackKey<?>, Identifier> stock, List<RecipePlan.Step> steps,
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
        if (VirtualProvisionerRecipeRegistry.descriptor(recipe) != null) return true;
        // Recipe#isIncomplete treats an empty vanilla getIngredients() list as invalid.
        // Many third-party machine recipes intentionally keep that list empty and expose
        // their inputs through their own API, so only apply the vanilla check when the
        // recipe actually uses the vanilla ingredient list.
        if (!recipe.isSpecial() && recipe.placementInfo().isImpossibleToPlace()) return false;
        return !RecipeResourceResolver.ingredients(recipe).isEmpty();
    }

    public static String family(RecipeHolder<?> holder)
    {
        var recipe = holder.value();
        var virtual = VirtualProvisionerRecipeRegistry.descriptor(recipe);
        if (virtual != null) return virtual.family();
        RecipeType<?> type = recipe.getType();
        String byType = family(type);
        if (byType != null) return byType;
        var recipeSerializer = recipe.getSerializer();
        Identifier serializer = recipeSerializer == null ? null
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
        Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? null : id.toString();
    }
}
