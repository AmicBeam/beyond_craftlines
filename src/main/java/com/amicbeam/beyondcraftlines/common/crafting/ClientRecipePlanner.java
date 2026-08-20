package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

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
    private static final Map<RecipeManager, Map<CatalogKey, Catalog>> CATALOG_CACHE =
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
        CatalogKey key = new CatalogKey(holders.stream().map(RecipeHolder::id).toList());
        synchronized (CATALOG_CACHE)
        {
            Map<CatalogKey, Catalog> catalogs = CATALOG_CACHE.computeIfAbsent(
                    level.getRecipeManager(), ignored -> new HashMap<>());
            Catalog cached = catalogs.get(key);
            return new CatalogBuilder(level, holders, key, cached);
        }
    }

    public static void clearCache() { CATALOG_CACHE.clear(); }

    private static Recipe captureRecipe(Level level, RecipeHolder<?> holder)
    {
        ItemStack result = holder.value().getResultItem(level.registryAccess());
        List<Slot> slots = new ArrayList<>();
        int slot = 0;
        for (var ingredient : holder.value().getIngredients())
        {
            int current = slot++;
            if (ingredient.isEmpty()) continue;
            LinkedHashMap<ResourceLocation, Candidate> candidates = new LinkedHashMap<>();
            for (ItemStack stack : ingredient.getItems())
            {
                ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
                candidates.putIfAbsent(item, new Candidate(item, Math.max(1, stack.getCount())));
            }
            if (!candidates.isEmpty()) slots.add(new Slot(current, List.copyOf(candidates.values()), false));
        }
        List<RecipePlan.IngredientSelection> baseline = slots.stream()
                .map(slotEntry -> new RecipePlan.IngredientSelection(
                        slotEntry.index(), slotEntry.candidates().getFirst().item())).toList();
        boolean[] reusable = SimulatedCrafting.reusableIngredientSlots(holder, level, baseline);
        slots = slots.stream().map(slotEntry -> new Slot(slotEntry.index(), slotEntry.candidates(),
                slotEntry.index() < reusable.length && reusable[slotEntry.index()])).toList();
        return new Recipe(holder.id(), RecipePlanningService.family(holder),
                BuiltInRegistries.ITEM.getKey(result.getItem()), Math.max(1, result.getCount()), slots);
    }

    public static Proposal plan(Catalog catalog, Map<ResourceLocation, Long> suppliedStock,
                                ResourceLocation target, long requested,
                                Map<ResourceLocation, ResourceLocation> manualRecipes,
                                Map<IngredientKey, ResourceLocation> manualIngredients,
                                int maxDepth, int maxNodes)
    {
        if (requested < 1 || maxDepth < 1 || maxNodes < 1) throw new IllegalArgumentException("invalid client plan");
        Map<ResourceLocation, List<Recipe>> byOutput = new HashMap<>();
        for (Recipe recipe : catalog.recipes())
            byOutput.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(recipe);
        byOutput.values().forEach(values -> values.sort(Comparator.comparing(recipe -> recipe.id().toString())));
        State state = new State(new HashMap<>(suppliedStock), new LinkedHashMap<>(), 0,
                new LinkedHashMap<>(), new LinkedHashMap<>());
        state.stock.put(target, 0L);
        ClientPlanningBudget budget = new ClientPlanningBudget(maxNodes);
        resolve(target, requested, byOutput, new HashSet<>(), state, manualRecipes, manualIngredients,
                0, maxDepth, budget);
        return new Proposal(state.recipes, state.ingredients, state.missing);
    }

    private static void resolve(ResourceLocation item, long needed, Map<ResourceLocation, List<Recipe>> byOutput,
                                Set<ResourceLocation> visiting, State state,
                                Map<ResourceLocation, ResourceLocation> manualRecipes,
                                Map<IngredientKey, ResourceLocation> manualIngredients,
                                int depth, int maxDepth, ClientPlanningBudget budget)
    {
        budget.enter();
        long available = state.stock.getOrDefault(item, 0L);
        long used = Math.min(available, needed);
        if (used > 0) state.stock.put(item, available - used);
        long remainder = needed - used;
        if (remainder == 0) return;
        if (depth >= maxDepth || !visiting.add(item))
        {
            state.missing.merge(item, remainder, SaturatingLongMath::add);
            return;
        }
        try
        {
            List<Recipe> candidates = byOutput.getOrDefault(item, List.of());
            ResourceLocation selected = manualRecipes.get(item);
            if (selected == null) selected = state.recipes.get(item);
            if (selected != null)
            {
                ResourceLocation choice = selected;
                candidates = candidates.stream().filter(recipe -> recipe.id().equals(choice)).toList();
            }
            if (candidates.isEmpty())
            {
                state.missing.merge(item, remainder, SaturatingLongMath::add);
                return;
            }
            if (!PlanningBranches.recipesRequireBranches(candidates.size()))
            {
                Recipe recipe = candidates.getFirst();
                ResourceLocation previous = state.recipes.putIfAbsent(item, recipe.id());
                if (previous != null && !previous.equals(recipe.id()))
                {
                    state.missing.merge(item, remainder, SaturatingLongMath::add);
                    return;
                }
                resolveRecipe(item, remainder, recipe, byOutput, new HashSet<>(visiting), state,
                        manualRecipes, manualIngredients, depth, maxDepth, budget);
                return;
            }
            State best = null;
            Recipe bestRecipe = null;
            for (Recipe recipe : candidates)
            {
                State branch = state.copy();
                ResourceLocation previous = branch.recipes.putIfAbsent(item, recipe.id());
                if (previous != null && !previous.equals(recipe.id())) continue;
                resolveRecipe(item, remainder, recipe, byOutput, new HashSet<>(visiting), branch,
                        manualRecipes, manualIngredients, depth, maxDepth, budget);
                if (best == null || compare(branch, recipe, best, bestRecipe) < 0)
                {
                    best = branch;
                    bestRecipe = recipe;
                }
            }
            if (best == null) state.missing.merge(item, remainder, SaturatingLongMath::add);
            else state.replaceWith(best);
        }
        finally { visiting.remove(item); }
    }

    private static void resolveRecipe(ResourceLocation item, long remainder, Recipe recipe,
                                      Map<ResourceLocation, List<Recipe>> byOutput, Set<ResourceLocation> visiting,
                                      State state, Map<ResourceLocation, ResourceLocation> manualRecipes,
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
                candidates = slot.candidates().stream().filter(candidate -> candidate.item().equals(choice)).toList();
                if (candidates.isEmpty()) throw new IllegalArgumentException("invalid ingredient proposal for " + key);
            }
            else
            {
                candidates = slot.candidates().stream().sorted(Comparator
                        .<Candidate>comparingLong(candidate -> state.stock.getOrDefault(candidate.item(), 0L)).reversed()
                        .thenComparing(candidate -> byOutput.containsKey(candidate.item()) ? 0 : 1)
                        .thenComparing(candidate -> candidate.item().toString())).toList();
            }
            options.add(candidates);
        }

        if (!PlanningBranches.ingredientsRequireBranches(options))
        {
            applyVariant(item, remainder, recipe, byOutput, visiting, state, manualRecipes,
                    manualIngredients, depth, maxDepth, budget,
                    options.stream().map(List::getFirst).toList());
            return;
        }

        State best = null;
        String bestKey = null;
        for (List<Candidate> variant : SingleSubstitutionVariants.from(options))
        {
            budget.enter();
            State branch = state.copy();
            if (!applyVariant(item, remainder, recipe, byOutput, visiting, branch, manualRecipes,
                    manualIngredients, depth, maxDepth, budget, variant)) continue;
            String key = variant.stream().map(candidate -> candidate.item().toString())
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

    private static boolean applyVariant(ResourceLocation item, long remainder, Recipe recipe,
                                        Map<ResourceLocation, List<Recipe>> byOutput,
                                        Set<ResourceLocation> visiting, State state,
                                        Map<ResourceLocation, ResourceLocation> manualRecipes,
                                        Map<IngredientKey, ResourceLocation> manualIngredients,
                                        int depth, int maxDepth, ClientPlanningBudget budget,
                                        List<Candidate> variant)
    {
        LinkedHashMap<ResourceLocation, Long> inputs = new LinkedHashMap<>();
        long crafts = SaturatingLongMath.ceilDiv(remainder, recipe.outputCount());
        for (int i = 0; i < variant.size(); i++)
        {
            Slot slot = recipe.slots().get(i);
            Candidate candidate = variant.get(i);
            IngredientKey key = new IngredientKey(recipe.id(), slot.index());
            ResourceLocation previous = state.ingredients.putIfAbsent(key, candidate.item());
            if (previous != null && !previous.equals(candidate.item())) return false;
            long inputAmount = slot.reusable() ? candidate.count()
                    : SaturatingLongMath.multiply(crafts, candidate.count());
            inputs.merge(candidate.item(), inputAmount, SaturatingLongMath::add);
        }
        for (var input : inputs.entrySet())
            resolve(input.getKey(), input.getValue(), byOutput, visiting, state, manualRecipes,
                    manualIngredients, depth + 1, maxDepth, budget);
        state.steps++;
        long produced = SaturatingLongMath.multiply(recipe.outputCount(), crafts);
        if (produced > remainder) state.stock.merge(item, produced - remainder, SaturatingLongMath::add);
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

    private static long missingAmount(Map<ResourceLocation, Long> missing)
    {
        long total = 0;
        for (long value : missing.values()) total = SaturatingLongMath.add(total, value);
        return total;
    }

    public record Catalog(List<Recipe> recipes) { public Catalog { recipes = List.copyOf(recipes); } }
    private record CatalogKey(List<ResourceLocation> recipes)
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
        {
            if (recipeBudget < 1 || complete()) return;
            int end = (int) Math.min(holders.size(), (long) next + recipeBudget);
            while (next < end) recipes.add(captureRecipe(level, holders.get(next++)));
            if (next == holders.size())
            {
                catalog = new Catalog(recipes);
                synchronized (CATALOG_CACHE)
                {
                    CATALOG_CACHE.computeIfAbsent(level.getRecipeManager(), ignored -> new HashMap<>())
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
    public record Recipe(ResourceLocation id, String family, ResourceLocation output, long outputCount,
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
    public record Candidate(ResourceLocation item, long count)
    {
        public Candidate { if (item == null || count < 1) throw new IllegalArgumentException("invalid candidate"); }
    }
    public record IngredientKey(ResourceLocation recipe, int slot) {}
    public record Proposal(Map<ResourceLocation, ResourceLocation> recipes,
                           Map<IngredientKey, ResourceLocation> ingredients,
                           Map<ResourceLocation, Long> missing)
    {
        public Proposal
        {
            recipes = Map.copyOf(recipes); ingredients = Map.copyOf(ingredients); missing = Map.copyOf(missing);
        }
        public boolean craftable() { return missing.isEmpty(); }
    }

    private static final class State
    {
        private Map<ResourceLocation, Long> stock;
        private Map<ResourceLocation, Long> missing;
        private int steps;
        private Map<ResourceLocation, ResourceLocation> recipes;
        private Map<IngredientKey, ResourceLocation> ingredients;
        private State(Map<ResourceLocation, Long> stock, Map<ResourceLocation, Long> missing, int steps,
                      Map<ResourceLocation, ResourceLocation> recipes,
                      Map<IngredientKey, ResourceLocation> ingredients)
        { this.stock = stock; this.missing = missing; this.steps = steps; this.recipes = recipes; this.ingredients = ingredients; }
        private State copy()
        { return new State(new HashMap<>(stock), new LinkedHashMap<>(missing), steps,
                new LinkedHashMap<>(recipes), new LinkedHashMap<>(ingredients)); }
        private void replaceWith(State state)
        { stock = state.stock; missing = state.missing; steps = state.steps; recipes = state.recipes; ingredients = state.ingredients; }
    }
}
