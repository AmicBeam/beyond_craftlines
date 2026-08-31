package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Pure client-side proposal search. Minecraft recipe objects are copied into {@link Catalog} before this runs,
 * so the expensive branch search can safely execute away from the render thread.
 */
public final class ClientRecipePlanner
{
    /** Wall-clock window shared by the preferred and fallback candidate searches. */
    public static final long SEARCH_TIME_LIMIT_NANOS = 3_000_000_000L;
    private static final Map<RecipeManager, Map<ResourceLocation, List<Recipe>>> CATALOG_CACHE =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private ClientRecipePlanner() {}

    public static Catalog capture(Level level, List<RecipeHolder<?>> holders)
    {
        CatalogBuilder builder = beginCapture(level, holders);
        while (!builder.complete()) builder.advance(Integer.MAX_VALUE);
        return builder.catalog();
    }

    public static CatalogBuilder beginCapture(Level level, List<RecipeHolder<?>> holders)
    {
        synchronized (CATALOG_CACHE)
        {
            Map<ResourceLocation, List<Recipe>> recipes = CATALOG_CACHE.computeIfAbsent(
                    level.getRecipeManager(), ignored -> new HashMap<>());
            return new CatalogBuilder(level, holders, recipes);
        }
    }

    public static void clearCache() { CATALOG_CACHE.clear(); }

    private static List<Recipe> captureRecipes(Level level, RecipeHolder<?> holder)
    {
        List<KeyAmount> outputs = RecipeOutputResolver.outputs(holder.value(), level.registryAccess());
        if (outputs.isEmpty()) throw new IllegalArgumentException("recipe has no supported output: " + holder.id());
        List<Recipe> captured = new ArrayList<>(outputs.size());
        for (KeyAmount output : outputs)
            captured.add(captureRecipeDirection(level, holder, output));
        return List.copyOf(captured);
    }

    private static Recipe captureRecipeDirection(Level level, RecipeHolder<?> holder, KeyAmount output)
    {
        List<RecipeResourceResolver.ResourceIngredient> recipeIngredients =
                RecipeResourceResolver.ingredientsForOutput(holder.value(), output.key());
        List<RecipePlan.IngredientSelection> baselineSelections = recipeIngredients.stream()
                .filter(ingredient -> ingredient.candidates().getFirst().key() instanceof ItemStackKey)
                .map(ingredient -> new RecipePlan.IngredientSelection(ingredient.slot(),
                        itemId(ingredient.candidates().getFirst().key()))).toList();
        List<Slot> slots = new ArrayList<>();
        for (var ingredient : recipeIngredients)
        {
            LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<>();
            for (KeyAmount value : ingredient.candidates())
            {
                Candidate candidate = new Candidate(value.key(), value.amount());
                if (value.key() instanceof ItemStackKey)
                {
                    List<RecipePlan.IngredientSelection> selections = new ArrayList<>(baselineSelections);
                    selections.removeIf(selection -> selection.slot() == ingredient.slot());
                    ResourceLocation selectedItem = itemId(value.key());
                    selections.add(new RecipePlan.IngredientSelection(ingredient.slot(), selectedItem));
                    KeyAmount proxy = SimulatedCrafting.bucketFluidInputs(holder, level, selections)
                            .get(ingredient.slot());
                    if (proxy != null) candidate = new Candidate(proxy.key(), proxy.amount(), selectedItem);
                }
                candidates.putIfAbsent(RecipeResourceResolver.sortKey(candidate.key()) + "|"
                        + Objects.toString(candidate.selectionItem(), ""), candidate);
            }
            if (!candidates.isEmpty()) slots.add(new Slot(ingredient.slot(),
                    List.copyOf(candidates.values()), VirtualInputUse.CONSUMED));
        }
        List<RecipePlan.IngredientSelection> baseline = slots.stream()
                .filter(slotEntry -> slotEntry.candidates().getFirst().selectionItem() != null)
                .map(slotEntry -> new RecipePlan.IngredientSelection(
                        slotEntry.index(), slotEntry.candidates().getFirst().selectionItem())).toList();
        boolean[] reusable = SimulatedCrafting.reusableIngredientSlots(holder, level, baseline);
        slots = slots.stream().map(slotEntry -> new Slot(slotEntry.index(), slotEntry.candidates(),
                VirtualInputUse.forRecipeSlot(holder.value(), slotEntry.index(),
                        slotEntry.index() < reusable.length && reusable[slotEntry.index()]))).toList();
        return new Recipe(holder.id(), RecipePlanningService.family(holder),
                output.key(), Math.max(1, output.amount()), slots);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                ResourceLocation target, long requested,
                                Map<ResourceLocation, ResourceLocation> manualRecipes,
                                Map<IngredientKey, ResourceLocation> manualIngredients,
                                int maxDepth, int maxNodes)
    {
        Map<String, ResourceLocation> converted = new LinkedHashMap<>();
        manualRecipes.forEach((output, recipe) -> converted.put(RecipeResourceResolver.sortKey(
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(output)))), recipe));
        return plan(catalog, suppliedStock,
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(target))), requested,
                converted, manualIngredients, maxDepth, maxNodes);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                IStackKey<?> target, long requested,
                                Map<String, ResourceLocation> manualRecipes,
                                Map<IngredientKey, ResourceLocation> manualIngredients,
                                int maxDepth, int maxNodes)
    {
        return plan(catalog, suppliedStock, target, requested, manualRecipes, manualIngredients,
                maxDepth, maxNodes, SEARCH_TIME_LIMIT_NANOS);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                IStackKey<?> target, long requested,
                                Map<String, ResourceLocation> manualRecipes,
                                Map<IngredientKey, ResourceLocation> manualIngredients,
                                int maxDepth, int maxNodes, long maxSearchNanos)
    {
        if (requested < 1 || maxDepth < 1 || maxNodes < 1 || maxSearchNanos < 1)
            throw new IllegalArgumentException("invalid client plan");
        Map<IStackKey<?>, List<Recipe>> byOutput = new LinkedHashMap<>();
        for (Recipe recipe : catalog.recipes())
            byOutput.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(recipe);
        byOutput.values().forEach(values -> values.sort(Comparator.comparing(recipe -> recipe.id().toString())));
        State state = new State(new MatchingStock<>(IStackKey::getTypeId, suppliedStock), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>(), 0,
                new LinkedHashMap<>(), new LinkedHashMap<>());
        ClientPlanningBudget budget = new ClientPlanningBudget(maxNodes, maxSearchNanos, System::nanoTime);
        resolve(target, requested,
                byOutput, new HashSet<>(), state, manualRecipes, manualIngredients,
                0, maxDepth, budget);
        return new Proposal(state.recipes, state.ingredients, state.missing, state.usedStock,
                budget.exhausted());
    }

    private static void resolve(IStackKey<?> resource, long needed, Map<IStackKey<?>, List<Recipe>> byOutput,
                                Set<IStackKey<?>> visiting, State state,
                                Map<String, ResourceLocation> manualRecipes,
                                Map<IngredientKey, ResourceLocation> manualIngredients,
                                int depth, int maxDepth, ClientPlanningBudget budget)
    {
        budget.checkCancellation();
        budget.visit(budgetIdentity(resource));
        long used = depth == 0 ? 0 : consume(state, resource, needed);
        long remainder = needed - used;
        if (remainder == 0) return;
        if (depth >= maxDepth)
        {
            state.missing.merge(resource, remainder, SaturatingLongMath::add);
            return;
        }
        if (!visiting.add(resource))
            throw new PlanningCycleBranch.Cycle();
        String resourceId = RecipeResourceResolver.resolutionKey(resource);
        try
        {
            List<Recipe> candidates = recipesFor(byOutput, resource);
            ResourceLocation selected = manualRecipes.get(resourceId);
            if (selected == null) selected = manualRecipes.get(RecipeResourceResolver.sortKey(resource));
            if (selected == null) selected = state.recipes.get(resourceId);
            if (selected != null)
            {
                ResourceLocation choice = selected;
                candidates = candidates.stream().filter(recipe -> recipe.id().equals(choice)).toList();
            }
            if (candidates.isEmpty())
            {
                state.missing.merge(resource, remainder, SaturatingLongMath::add);
                return;
            }
            if (!PlanningBranches.recipesRequireBranches(candidates.size()))
            {
                Recipe recipe = candidates.getFirst();
                State branch = state.copy();
                ResourceLocation previous = branch.recipes.putIfAbsent(resourceId, recipe.id());
                if (previous != null && !previous.equals(recipe.id()))
                {
                    state.missing.merge(resource, remainder, SaturatingLongMath::add);
                    return;
                }
                State attempted = branch;
                branch = PlanningCycleBranch.evaluate(state, () -> {
                    resolveRecipe(resource, remainder, recipe, byOutput, new HashSet<>(visiting), attempted,
                            manualRecipes, manualIngredients, depth, maxDepth, budget);
                    return attempted;
                }, baseline -> rejectCyclicCandidate(baseline, resource, remainder));
                state.replaceWith(branch);
                return;
            }
            State best = null;
            Recipe bestRecipe = null;
            for (Recipe recipe : candidates)
            {
                if (!PlanningBranches.shouldTryCandidate(best != null, budget)) break;
                State branch = state.copy();
                ResourceLocation previous = branch.recipes.putIfAbsent(resourceId, recipe.id());
                if (previous != null && !previous.equals(recipe.id())) continue;
                State attempted = branch;
                branch = PlanningCycleBranch.evaluate(state, () -> {
                    resolveRecipe(resource, remainder, recipe, byOutput, new HashSet<>(visiting), attempted,
                            manualRecipes, manualIngredients, depth, maxDepth, budget);
                    return attempted;
                }, baseline -> rejectCyclicCandidate(baseline, resource, remainder));
                if (best == null || compare(branch, recipe, best, bestRecipe) < 0)
                {
                    best = branch;
                    bestRecipe = recipe;
                }
                if (missingAmount(branch.missing) == 0) break;
            }
            if (best == null) state.missing.merge(resource, remainder, SaturatingLongMath::add);
            else state.replaceWith(best);
        }
        finally { visiting.remove(resource); }
    }

    private static void resolveRecipe(IStackKey<?> output, long remainder, Recipe recipe,
                                      Map<IStackKey<?>, List<Recipe>> byOutput, Set<IStackKey<?>> visiting,
                                      State state, Map<String, ResourceLocation> manualRecipes,
                                      Map<IngredientKey, ResourceLocation> manualIngredients,
                                      int depth, int maxDepth, ClientPlanningBudget budget)
    {
        List<List<Candidate>> options = new ArrayList<>();
        for (Slot slot : recipe.slots())
        {
            IngredientKey key = new IngredientKey(recipe.id(), slot.index());
            ResourceLocation fixed = manualIngredients.get(key);
            if (fixed == null) fixed = state.ingredients.get(key);
            List<Candidate> candidates;
            if (fixed != null)
            {
                ResourceLocation choice = fixed;
                candidates = slot.candidates().stream().filter(candidate ->
                        choice.equals(candidate.selectionItem())).toList();
                if (candidates.isEmpty()) throw new IllegalArgumentException("invalid ingredient proposal for " + key);
            }
            else
            {
                candidates = slot.candidates().stream().sorted(Comparator
                        .<Candidate>comparingLong(candidate -> available(state.stock, candidate.key())).reversed()
                        .thenComparing(candidate -> !recipesFor(byOutput, candidate.key()).isEmpty() ? 0 : 1)
                        .thenComparing(candidate -> RecipeResourceResolver.sortKey(candidate.key()))).toList();
            }
            options.add(candidates);
        }

        if (!PlanningBranches.ingredientsRequireBranches(options))
        {
            applyVariant(output, remainder, recipe, byOutput, visiting, state, manualRecipes,
                    manualIngredients, depth, maxDepth, budget,
                    options.stream().map(List::getFirst).toList());
            return;
        }

        State best = null;
        String bestKey = null;
        for (List<Candidate> variant : SingleSubstitutionVariants.from(options))
        {
            if (!PlanningBranches.shouldTryCandidate(best != null, budget)) break;
            State branch = state.copy();
            if (!applyVariant(output, remainder, recipe, byOutput, visiting, branch, manualRecipes,
                    manualIngredients, depth, maxDepth, budget, variant)) continue;
            String key = variant.stream().map(candidate -> RecipeResourceResolver.sortKey(candidate.key()))
                    .collect(java.util.stream.Collectors.joining("|"));
            if (best == null || compare(branch, recipe, best, recipe) < 0
                    || compare(branch, recipe, best, recipe) == 0 && key.compareTo(bestKey) < 0)
            {
                best = branch;
                bestKey = key;
            }
            if (missingAmount(branch.missing) == 0) break;
        }
        if (best == null) throw new IllegalArgumentException("no valid ingredient proposal for " + recipe.id());
        state.replaceWith(best);
    }

    private static boolean applyVariant(IStackKey<?> output, long remainder, Recipe recipe,
                                        Map<IStackKey<?>, List<Recipe>> byOutput,
                                        Set<IStackKey<?>> visiting, State state,
                                        Map<String, ResourceLocation> manualRecipes,
                                        Map<IngredientKey, ResourceLocation> manualIngredients,
                                        int depth, int maxDepth, ClientPlanningBudget budget,
                                        List<Candidate> variant)
    {
        LinkedHashMap<IStackKey<?>, Long> inputs = new LinkedHashMap<>();
        LinkedHashMap<IStackKey<?>, Long> reusableInputs = new LinkedHashMap<>();
        long seedPerCraft = 0;
        long consumedSeedPerCraft = 0;
        for (int i = 0; i < variant.size(); i++)
        {
            Slot slot = recipe.slots().get(i);
            Candidate candidate = variant.get(i);
            if (!StackKeyMatch.exact(output, candidate.key())) continue;
            seedPerCraft = SaturatingLongMath.add(seedPerCraft, candidate.count());
            if (!slot.use().sharedReusable())
                consumedSeedPerCraft = SaturatingLongMath.add(consumedSeedPerCraft, candidate.count());
        }
        SelfIncrementRecipe.Shape shape = SelfIncrementRecipe.analyze(
                recipe.outputCount(), seedPerCraft, consumedSeedPerCraft, remainder);
        long crafts = shape.crafts();
        for (int i = 0; i < variant.size(); i++)
        {
            Slot slot = recipe.slots().get(i);
            Candidate candidate = variant.get(i);
            IngredientKey key = new IngredientKey(recipe.id(), slot.index());
            if (candidate.selectionItem() != null)
            {
                ResourceLocation candidateItem = candidate.selectionItem();
                ResourceLocation previous = state.ingredients.putIfAbsent(key, candidateItem);
                if (previous != null && !previous.equals(candidateItem)) return false;
            }
            boolean selfInput = shape.selfIncrement() && StackKeyMatch.exact(output, candidate.key());
            long inputAmount = selfInput ? candidate.count()
                    : slot.use().requiredAmount(crafts, candidate.key(), candidate.count(), state.stock);
            (slot.use().sharedReusable() ? reusableInputs : inputs)
                    .merge(candidate.key(), inputAmount, SaturatingLongMath::add);
        }
        for (var input : inputs.entrySet())
            if (shape.selfIncrement() && StackKeyMatch.exact(output, input.getKey()))
                consumeLeaf(state, input.getKey(), input.getValue());
            else resolve(input.getKey(), input.getValue(), byOutput, visiting, state, manualRecipes,
                        manualIngredients, depth + 1, maxDepth, budget);
        for (var input : reusableInputs.entrySet())
        {
            if (shape.selfIncrement() && StackKeyMatch.exact(output, input.getKey()))
            {
                consumeLeaf(state, input.getKey(), input.getValue());
                continue;
            }
            long additional = PlanningDependencyBatcher.additionalReusableAmount(
                    state.reusableRequirements, input.getKey(), input.getValue());
            if (additional > 0)
                resolve(input.getKey(), additional, byOutput, visiting, state, manualRecipes,
                        manualIngredients, depth + 1, maxDepth, budget);
        }
        state.steps++;
        long produced = SaturatingLongMath.multiply(shape.netOutputPerCraft(), crafts);
        if (produced > remainder) state.stock.add(output, produced - remainder);
        return true;
    }

    private static void consumeLeaf(State state, IStackKey<?> key, long amount)
    {
        long used = consume(state, key, amount);
        if (used < amount) state.missing.merge(key, amount - used, SaturatingLongMath::add);
    }

    private static int compare(State left, Recipe leftRecipe, State right, Recipe rightRecipe)
    {
        int missing = Long.compare(missingAmount(left.missing), missingAmount(right.missing));
        if (missing != 0) return missing;
        int steps = Integer.compare(left.steps, right.steps);
        if (steps != 0) return steps;
        return leftRecipe.id().toString().compareTo(rightRecipe.id().toString());
    }

    private static long missingAmount(Map<?, Long> missing)
    {
        long total = 0;
        for (long value : missing.values()) total = SaturatingLongMath.add(total, value);
        return total;
    }

    private static State rejectCyclicCandidate(State baseline, IStackKey<?> resource, long remainder)
    {
        // Reject only this candidate. The cycle may close over an ancestor,
        // but sibling recipes for the current resource can still be viable.
        State rejected = baseline.copy();
        rejected.missing.merge(resource, remainder, SaturatingLongMath::add);
        return rejected;
    }

    public record Catalog(List<Recipe> recipes) { public Catalog { recipes = List.copyOf(recipes); } }

    public static final class CatalogBuilder
    {
        private final Level level;
        private final List<RecipeHolder<?>> allHolders;
        private final List<RecipeHolder<?>> holders;
        private final Map<ResourceLocation, List<Recipe>> captures = new HashMap<>();
        private final int total;
        private int next;
        private int completed;
        private Catalog catalog;

        private CatalogBuilder(Level level, List<RecipeHolder<?>> holders,
                               Map<ResourceLocation, List<Recipe>> cached)
        {
            this.level = level;
            this.allHolders = List.copyOf(holders);
            this.total = holders.size();
            List<RecipeHolder<?>> missing = new ArrayList<>();
            for (RecipeHolder<?> holder : holders)
            {
                List<Recipe> captured = cached.get(holder.id());
                if (captured == null) missing.add(holder);
                else
                {
                    captures.put(holder.id(), captured);
                    completed++;
                }
            }
            this.holders = List.copyOf(missing);
            if (this.holders.isEmpty()) finishCatalog();
        }

        public void advance(int recipeBudget)
        { advance(recipeBudget, Long.MAX_VALUE); }

        public void advance(int recipeBudget, long timeBudgetNanos)
        {
            if (recipeBudget < 1 || timeBudgetNanos < 1 || complete()) return;
            int end = (int) Math.min(holders.size(), (long) next + recipeBudget);
            int minimum = Math.min(16, recipeBudget);
            int processed = 0;
            long started = System.nanoTime();
            while (next < end && (processed < minimum || System.nanoTime() - started < timeBudgetNanos))
            {
                RecipeHolder<?> holder = holders.get(next++);
                List<Recipe> captured = List.copyOf(captureRecipes(level, holder));
                captures.put(holder.id(), captured);
                synchronized (CATALOG_CACHE)
                {
                    CATALOG_CACHE.computeIfAbsent(level.getRecipeManager(), ignored -> new HashMap<>())
                            .putIfAbsent(holder.id(), captured);
                }
                completed++;
                processed++;
            }
            if (next == holders.size())
                finishCatalog();
        }

        private void finishCatalog()
        {
            List<Recipe> ordered = new ArrayList<>();
            for (RecipeHolder<?> holder : allHolders)
                ordered.addAll(captures.getOrDefault(holder.id(), List.of()));
            catalog = new Catalog(ordered);
        }

        public boolean complete() { return catalog != null; }
        public int completedRecipes() { return completed; }
        public int totalRecipes() { return total; }
        public Catalog catalog()
        {
            if (catalog == null) throw new IllegalStateException("recipe catalog is still building");
            return catalog;
        }
    }
    public record Recipe(ResourceLocation id, String family, IStackKey<?> output, long outputCount,
                         List<Slot> slots)
    {
        public Recipe
        {
            Objects.requireNonNull(id); Objects.requireNonNull(family); Objects.requireNonNull(output);
            if (outputCount < 1) throw new IllegalArgumentException("invalid recipe output");
            slots = List.copyOf(slots);
        }
    }
    public record Slot(int index, List<Candidate> candidates, VirtualInputUse use)
    {
        public Slot
        {
            if (index < 0 || candidates.isEmpty() || use == null)
                throw new IllegalArgumentException("invalid recipe slot");
            candidates = List.copyOf(candidates);
        }
        public boolean reusable() { return use.sharedReusable(); }
    }
    public record Candidate(IStackKey<?> key, long count, ResourceLocation selectionItem)
    {
        public Candidate(IStackKey<?> key, long count)
        { this(key, count, key instanceof ItemStackKey ? itemId(key) : null); }
        public Candidate { if (key == null || key.isEmpty() || count < 1) throw new IllegalArgumentException("invalid candidate"); }
    }
    public record IngredientKey(ResourceLocation recipe, int slot) {}
    public record Proposal(Map<String, ResourceLocation> recipes,
                           Map<IngredientKey, ResourceLocation> ingredients,
                           Map<IStackKey<?>, Long> missing,
                           Map<IStackKey<?>, Long> extraction,
                           boolean searchExhausted)
    {
        public Proposal
        {
            recipes = Map.copyOf(recipes); ingredients = Map.copyOf(ingredients);
            missing = Map.copyOf(missing); extraction = Map.copyOf(extraction);
        }
        public boolean craftable() { return missing.isEmpty(); }
    }

    private static final class State
    {
        private MatchingStock<IStackKey<?>, ResourceLocation> stock;
        private Map<IStackKey<?>, Long> missing;
        private Map<IStackKey<?>, Long> reusableRequirements;
        private Map<IStackKey<?>, Long> usedStock;
        private int steps;
        private Map<String, ResourceLocation> recipes;
        private Map<IngredientKey, ResourceLocation> ingredients;
        private State(MatchingStock<IStackKey<?>, ResourceLocation> stock,
                      Map<IStackKey<?>, Long> missing,
                      Map<IStackKey<?>, Long> reusableRequirements,
                      Map<IStackKey<?>, Long> usedStock, int steps,
                      Map<String, ResourceLocation> recipes,
                      Map<IngredientKey, ResourceLocation> ingredients)
        { this.stock = stock; this.missing = missing; this.reusableRequirements = reusableRequirements;
            this.usedStock = usedStock;
            this.steps = steps; this.recipes = recipes; this.ingredients = ingredients; }
        private State copy()
        { return new State(stock.copy(), new LinkedHashMap<>(missing),
                new LinkedHashMap<>(reusableRequirements), new LinkedHashMap<>(usedStock), steps,
                new LinkedHashMap<>(recipes), new LinkedHashMap<>(ingredients)); }
        private void replaceWith(State state)
        { stock = state.stock; missing = state.missing; reusableRequirements = state.reusableRequirements;
            usedStock = state.usedStock;
            steps = state.steps; recipes = state.recipes; ingredients = state.ingredients; }
    }

    private static ResourceLocation itemId(IStackKey<?> key)
    { return BuiltInRegistries.ITEM.getKey(((ItemStackKey) key).getSource()); }

    private static String budgetIdentity(IStackKey<?> key)
    {
        return key instanceof ItemStackKey ? "item:" + itemId(key)
                : RecipeResourceResolver.sortKey(key);
    }

    private static List<Recipe> recipesFor(Map<IStackKey<?>, List<Recipe>> byOutput, IStackKey<?> resource)
    {
        for (var entry : byOutput.entrySet()) if (resource.isSame(entry.getKey())) return entry.getValue();
        return List.of();
    }

    private static long available(MatchingStock<IStackKey<?>, ResourceLocation> stock,
                                  IStackKey<?> requested)
    { return stock.available(requested.getTypeId(), requested::isSame); }

    private static long consume(State state, IStackKey<?> requested, long amount)
    {
        return state.stock.consume(requested.getTypeId(), requested::isSame, amount,
                (key, used) -> state.usedStock.merge(key, used, SaturatingLongMath::add));
    }
}
