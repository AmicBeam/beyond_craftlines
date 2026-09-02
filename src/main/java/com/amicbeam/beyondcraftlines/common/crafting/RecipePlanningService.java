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
        List<RecipeHolder<?>> base = VISIBLE_RECIPE_CACHE.computeIfAbsent(level.getRecipeManager(), manager ->
                manager.getRecipes().stream()
                .filter(RecipePlanningService::supported)
                .filter(holder -> !RecipeOutputResolver.outputs(
                        holder.value(), level.registryAccess()).isEmpty())
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList());
        return java.util.stream.Stream.concat(base.stream(), VirtualProvisionerRecipeRegistry.recipes().stream()
                        .filter(RecipePlanningService::supported)
                        .filter(holder -> !RecipeOutputResolver.outputs(
                                holder.value(), level.registryAccess()).isEmpty()))
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
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
        Set<String> base = LOADED_FAMILY_CACHE.computeIfAbsent(manager, ignored -> manager.getRecipes().stream()
                .map(RecipePlanningService::family).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        java.util.HashSet<String> families = new java.util.HashSet<>(base);
        VirtualProvisionerRecipeRegistry.recipes().stream().map(RecipePlanningService::family)
                .filter(java.util.Objects::nonNull).forEach(families::add);
        return Set.copyOf(families);
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

        Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput = new LinkedHashMap<>();
        Iterable<RecipeHolder<?>> candidates;
        if (mode == ResolutionMode.SEARCH) candidates = visibleRecipes(level);
        else
        {
            List<RecipeHolder<?>> selected = new ArrayList<>();
            for (ResourceLocation id : overrides.selectedRecipes())
            {
                RecipeHolder<?> holder = level.getRecipeManager().byKey(id)
                        .filter(candidate -> VanillaProvisionerRecipeTypes
                                .isPotentialNetworkExecutable(family(candidate)))
                        .or(() -> VirtualProvisionerRecipeRegistry.find(id)).orElse(null);
                if (holder != null) selected.add(holder);
            }
            candidates = selected;
        }
        for (RecipeHolder<?> holder : candidates)
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
        boolean targetIsSelfIncrementSeed = state.steps.stream().anyMatch(step ->
                step.selfIncrementSeed() > 0 && StackKeyMatch.exact(target, step.outputKey()));
        ManufacturingTargetReservations.removeFinalOutput(
                state.usedStock, target, targetIsSelfIncrementSeed);
        return new RecipePlan(target, amount, state.steps, state.missing.entrySet().stream()
                .map(entry -> new RecipePlan.Material(entry.getKey(), entry.getValue())).toList(),
                state.usedStock.entrySet().stream()
                        .map(entry -> new RecipePlan.ReservedMaterial(entry.getKey(), entry.getValue())).toList());
    }

    private static void resolve(ServerLevel level, IStackKey<?> resource, long needed,
                                Ingredient requiredIngredient,
                                Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                Set<IStackKey<?>> visiting, PlanningState state,
                                RecipeResolutionOverrides overrides, ResolutionMode mode, int depth, int maxDepth,
                                PlanningBudget budget)
    {
        if (mode == ResolutionMode.SEARCH) budget.checkTime();
        // A root order always manufactures its full amount, but its existing stock must remain
        // available to a self-increment child as the minimum seed.
        long used = depth == 0 ? 0 : state.stock.consume(resource.getTypeId(), key -> StackKeyMatch.exact(resource, key)
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
        if (!visiting.add(resource))
            throw new PlanningCycleBranch.Cycle();

        try
        {
            List<RecipeHolder<?>> candidates = recipesFor(byOutput, resource);
            if (requiredIngredient != null) candidates = candidates.stream().filter(holder ->
                    RecipeOutputResolver.matchesIngredient(holder.value(), requiredIngredient,
                            level.registryAccess())).toList();
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
            ResourceLocation bestRecipe = null;
            PlanningState cyclicFallback = null;
            boolean foundNonCyclic = false;
            for (RecipeHolder<?> holder : candidates)
            {
                if (mode == ResolutionMode.SEARCH && foundNonCyclic) budget.enterBranch();
                var evaluated = PlanningCycleBranch.evaluateWithStatus(state, () -> {
                    PlanningState attempted = state.copy();
                    resolveRecipe(level, resource, remainder, holder, byOutput, new HashSet<>(visiting), attempted,
                            overrides, mode, depth, maxDepth, budget);
                    return attempted;
                }, baseline -> rejectCyclicCandidate(baseline, resource, remainder));
                PlanningState branch = evaluated.state();
                if (evaluated.cyclic())
                {
                    if (cyclicFallback == null) cyclicFallback = branch;
                    continue;
                }
                foundNonCyclic = true;
                if (best == null || compare(branch, holder.id(), best, bestRecipe) < 0)
                {
                    best = branch;
                    bestRecipe = holder.id();
                }
            }
            if (best != null) state.replaceWith(best);
            else if (cyclicFallback != null) state.replaceWith(cyclicFallback);
            else state.missing.merge(resource, remainder, SaturatingLongMath::add);
        }
        finally { visiting.remove(resource); }
    }

    private static void resolveRecipe(ServerLevel level, IStackKey<?> outputKey, long remainder,
                                      RecipeHolder<?> holder,
                                      Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                      Set<IStackKey<?>> visiting, PlanningState state,
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
            try
            {
                resolveRecipeVariant(level, outputKey, remainder, holder, byOutput, visiting, branch,
                        overrides, mode, depth, maxDepth, budget, variant);
            }
            catch (PlanningCycleBranch.Cycle ignored) { continue; }
            String selectionKey = variant.stream().map(value -> RecipeResourceResolver.resolutionKey(value.key()))
                    .collect(java.util.stream.Collectors.joining("|"));
            int comparison = best == null ? -1 : compare(branch, holder.id(), best, holder.id());
            if (best == null || comparison < 0 || comparison == 0 && selectionKey.compareTo(bestSelectionKey) < 0)
            {
                best = branch;
                bestSelectionKey = selectionKey;
            }
            if (missingAmount(branch.missing) == 0) break;
        }
        if (best == null) throw new PlanningCycleBranch.Cycle();
        state.replaceWith(best);
    }

    private static void resolveRecipeVariant(ServerLevel level, IStackKey<?> outputKey, long remainder,
                                             RecipeHolder<?> holder,
                                             Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                             Set<IStackKey<?>> visiting, PlanningState state,
                                             RecipeResolutionOverrides overrides, ResolutionMode mode,
                                             int depth, int maxDepth,
                                             PlanningBudget budget,
                                             List<KeyAmount> variant)
    {
        int dependencyStart = state.steps.size();
        List<KeyAmount> recipeOutputs = RecipeOutputResolver.outputs(holder.value(), level.registryAccess());
        KeyAmount result = recipeOutputs.stream()
                .filter(value -> RecipeIoProfileRegistry.outputMatches(
                        holder.value(), outputKey, value.key())).findFirst().orElseThrow();
        long perCraft = Math.max(1, result.amount());
        List<RecipePlan.Material> inputs = new ArrayList<>();
        List<PlanningDependencyBatcher.Entry<IStackKey<?>>> dependencyInputs = new ArrayList<>();
        List<PlanningDependencyBatcher.Entry<IStackKey<?>>> reusableDependencyInputs = new ArrayList<>();
        Map<IStackKey<?>, Ingredient> dependencyIngredients = new LinkedHashMap<>();
        List<RecipeResourceResolver.ResourceIngredient> recipeIngredients =
                RecipeResourceResolver.ingredientsForOutput(holder.value(), outputKey);
        List<RecipePlan.IngredientSelection> selections = new ArrayList<>();
        Map<Integer, ResourceLocation> sampleItems = new LinkedHashMap<>();
        for (int i = 0; i < variant.size(); i++)
            if (variant.get(i).key() instanceof ItemStackKey itemKey)
            {
                ResourceLocation item = BuiltInRegistries.ITEM.getKey(itemKey.getSource());
                int slot = recipeIngredients.get(i).slot();
                selections.add(new RecipePlan.IngredientSelection(
                        slot, IngredientSelectionKey.exact(variant.get(i).key())));
                sampleItems.put(slot, item);
            }
        boolean[] reusableSlots = SimulatedCrafting.reusableIngredientSlots(holder, level, selections);
        Map<Integer, KeyAmount> fluidProxies = SimulatedCrafting.bucketFluidInputs(holder, level, selections);
        List<RecipePlan.IngredientSelection> finalSelections = new ArrayList<>();
        List<KeyAmount> selectedChoices = new ArrayList<>(variant.size());
        for (int i = 0; i < variant.size(); i++)
        {
            RecipeResourceResolver.ResourceIngredient ingredient = recipeIngredients.get(i);
            KeyAmount raw = variant.get(i);
            KeyAmount proxy = fluidProxies.get(ingredient.slot());
            String override = overrides.ingredientFor(holder.id(), ingredient.slot());
            boolean forceFluid = FluidContainerChoice.isProxy(override);
            boolean forceItem = override != null && !forceFluid;
            long availableFluid = proxy == null ? 0 : state.stock.available(
                    proxy.key().getTypeId(), key -> StackKeyMatch.exact(proxy.key(), key));
            boolean useFluid = FluidContainerPolicy.useFluid(raw.key() instanceof
                    com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey,
                    proxy != null, forceFluid, forceItem, availableFluid);
            KeyAmount choice = useFluid && proxy != null ? proxy : raw;
            selectedChoices.add(choice);
            ResourceLocation item = sampleItems.get(ingredient.slot());
            if (item == null && override != null) item = FluidContainerChoice.itemOrNull(override);
            if (item != null) finalSelections.add(new RecipePlan.IngredientSelection(ingredient.slot(),
                    useFluid ? FluidContainerChoice.proxy(item).toString()
                            : IngredientSelectionKey.exact(raw.key())));
        }
        long seedPerCraft = 0;
        long consumedSeedPerCraft = 0;
        for (int i = 0; i < variant.size(); i++)
        {
            RecipeResourceResolver.ResourceIngredient ingredient = recipeIngredients.get(i);
            KeyAmount choice = selectedChoices.get(i);
            VirtualInputUse use = VirtualInputUse.forRecipeSlot(holder.value(), ingredient.slot(),
                    ingredient.slot() < reusableSlots.length && reusableSlots[ingredient.slot()]);
            if (!StackKeyMatch.exact(outputKey, choice.key())) continue;
            seedPerCraft = SaturatingLongMath.add(seedPerCraft, choice.amount());
            if (!use.sharedReusable())
                consumedSeedPerCraft = SaturatingLongMath.add(consumedSeedPerCraft, choice.amount());
        }
        SelfIncrementRecipe.Shape shape = SelfIncrementRecipe.analyze(
                perCraft, seedPerCraft, consumedSeedPerCraft, remainder);
        long crafts = shape.crafts();
        for (int i = 0; i < variant.size(); i++)
        {
            RecipeResourceResolver.ResourceIngredient ingredient = recipeIngredients.get(i);
            KeyAmount choice = selectedChoices.get(i);
            VirtualInputUse use = VirtualInputUse.forRecipeSlot(holder.value(), ingredient.slot(),
                    ingredient.slot() < reusableSlots.length && reusableSlots[ingredient.slot()]);
            boolean reusable = use.sharedReusable();
            boolean selfInput = shape.selfIncrement() && StackKeyMatch.exact(outputKey, choice.key());
            long inputAmount = selfInput ? choice.amount()
                    : use.requiredAmount(crafts, choice.key(), choice.amount(), state.stock);
            inputs.add(new RecipePlan.Material(choice.key(), inputAmount, ingredient.slot(),
                    ingredient.inputGroup()));
            (reusable ? reusableDependencyInputs : dependencyInputs)
                    .add(new PlanningDependencyBatcher.Entry<>(choice.key(), inputAmount));
            dependencyIngredients.putIfAbsent(choice.key(), choice.key() instanceof
                    com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey
                    ? null : ingredient.itemIngredient());
        }
        for (var dependency : PlanningDependencyBatcher.aggregate(dependencyInputs).entrySet())
            if (shape.selfIncrement() && StackKeyMatch.exact(outputKey, dependency.getKey()))
                consumeLeaf(dependency.getKey(), dependency.getValue(), state);
            else resolve(level, dependency.getKey(), dependency.getValue(),
                        dependencyIngredients.get(dependency.getKey()), byOutput, visiting, state,
                        overrides, mode, depth + 1, maxDepth, budget);
        for (var dependency : PlanningDependencyBatcher.aggregate(reusableDependencyInputs).entrySet())
        {
            if (shape.selfIncrement() && StackKeyMatch.exact(outputKey, dependency.getKey()))
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
        state.steps.add(new RecipePlan.Step(holder.id(), family(holder), outputKey,
                perCraft, crafts, inputs, finalSelections, dependencies, shape.seed()));
        long produced = SaturatingLongMath.multiply(shape.netOutputPerCraft(), crafts);
        long surplus = produced > remainder ? produced - remainder : 0;
        if (surplus > 0) state.stock.add(outputKey, surplus);
    }

    private static void consumeLeaf(IStackKey<?> requested, long amount, PlanningState state)
    {
        long used = state.stock.consume(requested.getTypeId(), key -> StackKeyMatch.exact(requested, key), amount,
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
        String selected = overrides.ingredientFor(recipe, slot);
        if (selected != null)
        {
            if (FluidContainerChoice.isProxy(selected))
            {
                List<KeyAmount> fluids = ingredient.candidates().stream().filter(choice -> choice.key() instanceof
                        com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey).toList();
                if (!fluids.isEmpty()) return fluids;
                ResourceLocation container = FluidContainerChoice.itemOrNull(selected);
                if (container != null) selected = container.toString();
            }
            for (KeyAmount choice : ingredient.candidates())
                if (IngredientSelectionKey.matches(selected, choice.key())) return List.of(choice);
            throw new IllegalArgumentException("selected ingredient is invalid for " + recipe
                    + " slot " + slot + ": " + selected);
        }
        if (mode != ResolutionMode.SEARCH && ingredient.candidates().stream()
                .anyMatch(choice -> choice.key() instanceof ItemStackKey))
            throw new IllegalArgumentException("client proposal is incomplete");
        Comparator<KeyAmount> comparator = Comparator.<KeyAmount>comparingLong(value -> stock.available(
                        value.key().getTypeId(), key -> StackKeyMatch.exact(value.key(), key))).reversed()
                .thenComparing(value -> !recipesFor(byOutput, value.key()).isEmpty() ? 0 : 1)
                .thenComparing(value -> RecipeResourceResolver.resolutionKey(value.key()));
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
        // Reject only this candidate. The cycle may close over an ancestor,
        // but sibling recipes for the current resource can still be viable.
        PlanningState rejected = baseline.copy();
        rejected.missing.merge(resource, remainder, SaturatingLongMath::add);
        return rejected;
    }

    private static List<RecipeHolder<?>> recipesFor(Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                                     IStackKey<?> resource)
    {
        for (var entry : byOutput.entrySet())
            if (StackKeyMatch.exact(resource, entry.getKey())) return entry.getValue();
        for (var entry : byOutput.entrySet())
        {
            List<RecipeHolder<?>> configured = entry.getValue().stream().filter(holder ->
                    RecipeIoProfileRegistry.outputMatches(holder.value(), resource, entry.getKey())).toList();
            if (!configured.isEmpty()) return configured;
        }
        List<String> sameItemCandidates = byOutput.entrySet().stream()
                .filter(entry -> resource.isSame(entry.getKey()) || entry.getKey().isSame(resource))
                .limit(16).map(entry -> OrderDiagnostics.resource(entry.getKey()) + "="
                        + entry.getValue().stream().map(holder -> holder.id().toString()).toList()).toList();
        if (!sameItemCandidates.isEmpty()) OrderDiagnostics.LOGGER.warn(
                "{} server dependency exact miss requested={} sameItemCandidates={}",
                OrderDiagnostics.PREFIX, OrderDiagnostics.resource(resource), sameItemCandidates);
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
        if (VirtualProvisionerRecipeRegistry.descriptor(recipe) != null) return true;
        // Resolving the actual candidates is both the completeness check and the safest
        // compatibility boundary. Some third-party getIngredients() implementations
        // mutate cached stacks and can throw before isIncomplete() can be evaluated.
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
        if (type == RecipeType.STONECUTTING) return "minecraft:stonecutting";
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? null : id.toString();
    }
}
