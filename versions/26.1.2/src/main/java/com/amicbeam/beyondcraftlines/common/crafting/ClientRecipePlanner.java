package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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
    private static final Map<Level, Map<CatalogKey, Catalog>> CATALOG_CACHE =
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
        CatalogKey key = new CatalogKey(holders.stream().map(holder -> holder.id().identifier()).toList());
        synchronized (CATALOG_CACHE)
        {
            Map<CatalogKey, Catalog> catalogs = CATALOG_CACHE.computeIfAbsent(
                    level, ignored -> new HashMap<>());
            Catalog cached = catalogs.get(key);
            return new CatalogBuilder(level, holders, key, cached);
        }
    }

    public static void clearCache() { CATALOG_CACHE.clear(); }

    private static List<Recipe> captureRecipes(Level level, RecipeHolder<?> holder)
    {
        List<KeyAmount> outputs = RecipeOutputResolver.outputs(holder.value(), level);
        if (outputs.isEmpty()) throw new IllegalArgumentException("recipe has no supported output: " + holder.id());
        List<Recipe> captured = new ArrayList<>(outputs.size());
        for (KeyAmount output : outputs)
            captured.add(captureRecipeDirection(level, holder, output));
        return List.copyOf(captured);
    }

    private static Recipe captureRecipeDirection(Level level, RecipeHolder<?> holder, KeyAmount output)
    {
        List<Slot> slots = new ArrayList<>();
        for (var ingredient : RecipeResourceResolver.ingredientsForOutput(holder.value(), output.key()))
        {
            LinkedHashMap<IStackKey<?>, Candidate> candidates = new LinkedHashMap<>();
            for (KeyAmount value : ingredient.candidates())
                candidates.putIfAbsent(value.key(), new Candidate(value.key(), value.amount()));
            if (!candidates.isEmpty()) slots.add(new Slot(ingredient.slot(),
                    List.copyOf(candidates.values()), false));
        }
        List<RecipePlan.IngredientSelection> baseline = slots.stream()
                .filter(slotEntry -> slotEntry.candidates().getFirst().key() instanceof ItemStackKey)
                .map(slotEntry -> new RecipePlan.IngredientSelection(
                        slotEntry.index(), itemId(slotEntry.candidates().getFirst().key()))).toList();
        boolean[] reusable = SimulatedCrafting.reusableIngredientSlots(holder, level, baseline);
        slots = slots.stream().map(slotEntry -> new Slot(slotEntry.index(), slotEntry.candidates(),
                slotEntry.index() < reusable.length && reusable[slotEntry.index()])).toList();
        return new Recipe(holder.id().identifier(), RecipePlanningService.family(holder),
                output.key(), Math.max(1, output.amount()), slots);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                Identifier target, long requested,
                                Map<Identifier, Identifier> manualRecipes,
                                Map<IngredientKey, Identifier> manualIngredients,
                                int maxDepth, int maxNodes)
    {
        Map<String, Identifier> converted = new LinkedHashMap<>();
        manualRecipes.forEach((output, recipe) -> converted.put(RecipeResourceResolver.sortKey(
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(output)))), recipe));
        return plan(catalog, suppliedStock,
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(target))), requested,
                converted, manualIngredients, maxDepth, maxNodes);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                IStackKey<?> target, long requested,
                                Map<String, Identifier> manualRecipes,
                                Map<IngredientKey, Identifier> manualIngredients,
                                int maxDepth, int maxNodes)
    {
        if (requested < 1 || maxDepth < 1 || maxNodes < 1) throw new IllegalArgumentException("invalid client plan");
        Map<IStackKey<?>, List<Recipe>> byOutput = new LinkedHashMap<>();
        for (Recipe recipe : catalog.recipes())
            byOutput.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(recipe);
        byOutput.values().forEach(values -> values.sort(Comparator.comparing(recipe -> recipe.id().toString())));
        State state = new State(new LinkedHashMap<>(suppliedStock), new LinkedHashMap<>(),
                new LinkedHashMap<>(), 0, new LinkedHashMap<>(), new LinkedHashMap<>());
        state.stock.entrySet().removeIf(entry -> target.isSame(entry.getKey()));
        ClientPlanningBudget budget = new ClientPlanningBudget(maxNodes);
        resolve(target, requested,
                byOutput, new HashSet<>(), state, manualRecipes, manualIngredients,
                0, maxDepth, budget);
        return new Proposal(state.recipes, state.ingredients, state.missing);
    }

    private static void resolve(IStackKey<?> resource, long needed, Map<IStackKey<?>, List<Recipe>> byOutput,
                                Set<String> visiting, State state,
                                Map<String, Identifier> manualRecipes,
                                Map<IngredientKey, Identifier> manualIngredients,
                                int depth, int maxDepth, ClientPlanningBudget budget)
    {
        budget.checkCancellation();
        budget.visit(budgetIdentity(resource));
        long used = consume(state.stock, resource, needed);
        long remainder = needed - used;
        if (remainder == 0) return;
        if (depth >= maxDepth)
        {
            state.missing.merge(resource, remainder, SaturatingLongMath::add);
            return;
        }
        if (!visiting.add(RecipeResourceResolver.sortKey(resource)))
            throw new CyclicRecipePathException(resource);
        String resourceId = RecipeResourceResolver.sortKey(resource);
        try
        {
            List<Recipe> candidates = recipesFor(byOutput, resource);
            Identifier selected = manualRecipes.get(resourceId);
            if (selected == null) selected = state.recipes.get(resourceId);
            if (selected != null)
            {
                Identifier choice = selected;
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
                Identifier previous = branch.recipes.putIfAbsent(resourceId, recipe.id());
                if (previous != null && !previous.equals(recipe.id()))
                {
                    state.missing.merge(resource, remainder, SaturatingLongMath::add);
                    return;
                }
                try
                {
                    resolveRecipe(resource, remainder, recipe, byOutput, new HashSet<>(visiting), branch,
                            manualRecipes, manualIngredients, depth, maxDepth, budget);
                    state.replaceWith(branch);
                }
                catch (CyclicRecipePathException cycle)
                {
                    if (!resource.isSame(cycle.resource)) throw cycle;
                    state.missing.merge(resource, remainder, SaturatingLongMath::add);
                }
                return;
            }
            State best = null;
            Recipe bestRecipe = null;
            for (Recipe recipe : candidates)
            {
                // An incomplete candidate is not a usable result. Keep searching for feasibility
                // even after the soft optimization budget is exhausted.
                if (best != null && missingAmount(best.missing) == 0 && !budget.canOptimize()) break;
                State branch = state.copy();
                Identifier previous = branch.recipes.putIfAbsent(resourceId, recipe.id());
                if (previous != null && !previous.equals(recipe.id())) continue;
                try
                {
                    resolveRecipe(resource, remainder, recipe, byOutput, new HashSet<>(visiting), branch,
                            manualRecipes, manualIngredients, depth, maxDepth, budget);
                }
                catch (CyclicRecipePathException cycle)
                {
                    if (!resource.isSame(cycle.resource)) throw cycle;
                    branch = state.copy();
                    branch.missing.merge(resource, remainder, SaturatingLongMath::add);
                }
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
        finally { visiting.remove(RecipeResourceResolver.sortKey(resource)); }
    }

    private static void resolveRecipe(IStackKey<?> output, long remainder, Recipe recipe,
                                      Map<IStackKey<?>, List<Recipe>> byOutput, Set<String> visiting,
                                      State state, Map<String, Identifier> manualRecipes,
                                      Map<IngredientKey, Identifier> manualIngredients,
                                      int depth, int maxDepth, ClientPlanningBudget budget)
    {
        List<List<Candidate>> options = new ArrayList<>();
        for (Slot slot : recipe.slots())
        {
            IngredientKey key = new IngredientKey(recipe.id(), slot.index());
            Identifier fixed = manualIngredients.get(key);
            if (fixed == null) fixed = state.ingredients.get(key);
            List<Candidate> candidates;
            if (fixed != null)
            {
                Identifier choice = fixed;
                candidates = slot.candidates().stream().filter(candidate -> candidate.key() instanceof ItemStackKey
                        && itemId(candidate.key()).equals(choice)).toList();
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
            if (best != null && missingAmount(best.missing) == 0 && !budget.canOptimize()) break;
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
                                        Set<String> visiting, State state,
                                        Map<String, Identifier> manualRecipes,
                                        Map<IngredientKey, Identifier> manualIngredients,
                                        int depth, int maxDepth, ClientPlanningBudget budget,
                                        List<Candidate> variant)
    {
        LinkedHashMap<IStackKey<?>, Long> inputs = new LinkedHashMap<>();
        LinkedHashMap<IStackKey<?>, Long> reusableInputs = new LinkedHashMap<>();
        long crafts = SaturatingLongMath.ceilDiv(remainder, recipe.outputCount());
        for (int i = 0; i < variant.size(); i++)
        {
            Slot slot = recipe.slots().get(i);
            Candidate candidate = variant.get(i);
            IngredientKey key = new IngredientKey(recipe.id(), slot.index());
            if (candidate.key() instanceof ItemStackKey)
            {
                Identifier candidateItem = itemId(candidate.key());
                Identifier previous = state.ingredients.putIfAbsent(key, candidateItem);
                if (previous != null && !previous.equals(candidateItem)) return false;
            }
            long inputAmount = slot.reusable() ? candidate.count()
                    : SaturatingLongMath.multiply(crafts, candidate.count());
            (slot.reusable() ? reusableInputs : inputs)
                    .merge(candidate.key(), inputAmount, SaturatingLongMath::add);
        }
        for (var input : inputs.entrySet())
            resolve(input.getKey(), input.getValue(), byOutput, visiting, state, manualRecipes,
                    manualIngredients, depth + 1, maxDepth, budget);
        for (var input : reusableInputs.entrySet())
        {
            long additional = PlanningDependencyBatcher.additionalReusableAmount(
                    state.reusableRequirements, input.getKey(), input.getValue());
            if (additional > 0)
                resolve(input.getKey(), additional, byOutput, visiting, state, manualRecipes,
                        manualIngredients, depth + 1, maxDepth, budget);
        }
        state.steps++;
        long produced = SaturatingLongMath.multiply(recipe.outputCount(), crafts);
        if (produced > remainder) state.stock.merge(output, produced - remainder, SaturatingLongMath::add);
        return true;
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

    private static final class CyclicRecipePathException extends RuntimeException
    {
        private final IStackKey<?> resource;
        private CyclicRecipePathException(IStackKey<?> resource) { this.resource = resource; }
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    public record Catalog(List<Recipe> recipes) { public Catalog { recipes = List.copyOf(recipes); } }
    private record CatalogKey(List<Identifier> recipes)
    { private CatalogKey { recipes = List.copyOf(recipes); } }

    public static final class CatalogBuilder
    {
        private final Level level;
        private final List<RecipeHolder<?>> holders;
        private final CatalogKey key;
        private final List<Recipe> recipes = new ArrayList<>();
        private int next;
        private Catalog catalog;

        private CatalogBuilder(Level level, List<RecipeHolder<?>> holders, CatalogKey key, Catalog cached)
        {
            this.level = level;
            this.holders = List.copyOf(holders);
            this.key = key;
            this.catalog = cached;
            if (cached != null) next = holders.size();
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
                recipes.addAll(captureRecipes(level, holders.get(next++)));
                processed++;
            }
            if (next == holders.size())
            {
                catalog = new Catalog(recipes);
                synchronized (CATALOG_CACHE)
                {
                    CATALOG_CACHE.computeIfAbsent(level, ignored -> new HashMap<>())
                            .put(key, catalog);
                }
            }
        }

        public boolean complete() { return catalog != null; }
        public int completedRecipes() { return next; }
        public int totalRecipes() { return holders.size(); }
        public Catalog catalog()
        {
            if (catalog == null) throw new IllegalStateException("recipe catalog is still building");
            return catalog;
        }
    }
    public record Recipe(Identifier id, String family, IStackKey<?> output, long outputCount,
                         List<Slot> slots)
    {
        public Recipe
        {
            Objects.requireNonNull(id); Objects.requireNonNull(family); Objects.requireNonNull(output);
            if (outputCount < 1) throw new IllegalArgumentException("invalid recipe output");
            slots = List.copyOf(slots);
        }
    }
    public record Slot(int index, List<Candidate> candidates, boolean reusable)
    {
        public Slot
        {
            if (index < 0 || candidates.isEmpty()) throw new IllegalArgumentException("invalid recipe slot");
            candidates = List.copyOf(candidates);
        }
    }
    public record Candidate(IStackKey<?> key, long count)
    {
        public Candidate { if (key == null || key.isEmpty() || count < 1) throw new IllegalArgumentException("invalid candidate"); }
    }
    public record IngredientKey(Identifier recipe, int slot) {}
    public record Proposal(Map<String, Identifier> recipes,
                           Map<IngredientKey, Identifier> ingredients,
                           Map<IStackKey<?>, Long> missing)
    {
        public Proposal
        {
            recipes = Map.copyOf(recipes); ingredients = Map.copyOf(ingredients); missing = Map.copyOf(missing);
        }
        public boolean craftable() { return missing.isEmpty(); }
    }

    private static final class State
    {
        private Map<IStackKey<?>, Long> stock;
        private Map<IStackKey<?>, Long> missing;
        private Map<IStackKey<?>, Long> reusableRequirements;
        private int steps;
        private Map<String, Identifier> recipes;
        private Map<IngredientKey, Identifier> ingredients;
        private State(Map<IStackKey<?>, Long> stock, Map<IStackKey<?>, Long> missing,
                      Map<IStackKey<?>, Long> reusableRequirements, int steps,
                      Map<String, Identifier> recipes,
                      Map<IngredientKey, Identifier> ingredients)
        { this.stock = stock; this.missing = missing; this.reusableRequirements = reusableRequirements;
            this.steps = steps; this.recipes = recipes; this.ingredients = ingredients; }
        private State copy()
        { return new State(new LinkedHashMap<>(stock), new LinkedHashMap<>(missing),
                new LinkedHashMap<>(reusableRequirements), steps,
                new LinkedHashMap<>(recipes), new LinkedHashMap<>(ingredients)); }
        private void replaceWith(State state)
        { stock = state.stock; missing = state.missing; reusableRequirements = state.reusableRequirements;
            steps = state.steps; recipes = state.recipes; ingredients = state.ingredients; }
    }

    private static Identifier itemId(IStackKey<?> key)
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

    private static long available(Map<IStackKey<?>, Long> stock, IStackKey<?> requested)
    {
        long result = 0;
        for (var entry : stock.entrySet())
            if (requested.isSame(entry.getKey())) result = SaturatingLongMath.add(result, entry.getValue());
        return result;
    }

    private static long consume(Map<IStackKey<?>, Long> stock, IStackKey<?> requested, long amount)
    {
        long remaining = amount;
        for (var entry : stock.entrySet())
        {
            if (remaining <= 0) break;
            if (entry.getValue() <= 0 || !requested.isSame(entry.getKey())) continue;
            long used = Math.min(remaining, entry.getValue());
            entry.setValue(entry.getValue() - used);
            remaining -= used;
        }
        return amount - remaining;
    }
}
