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

/**
 * Pure client-side proposal search. Minecraft recipe objects are copied into {@link Catalog} before this runs,
 * so the expensive branch search can safely execute away from the render thread.
 */
public final class ClientRecipePlanner
{
    /** Wall-clock window shared by the preferred and fallback candidate searches. */
    public static final long SEARCH_TIME_LIMIT_NANOS = 3_000_000_000L;
    private ClientRecipePlanner() {}

    public static Catalog capture(Level level, List<RecipeHolder<?>> holders)
    {
        CatalogBuilder builder = beginCapture(level, holders);
        while (!builder.complete()) builder.advance(Long.MAX_VALUE);
        return builder.catalog();
    }

    public static CatalogBuilder beginCapture(Level level, List<RecipeHolder<?>> holders)
    { return new CatalogBuilder(level, holders); }
    public static CatalogBuilder restored(Catalog catalog,int total){return new CatalogBuilder(catalog,total);}

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                Identifier target, long requested,
                                Map<Identifier, Identifier> manualRecipes,
                                Map<IngredientKey, Identifier> manualIngredients,
                                int maxDepth, int maxNodes)
    {
        Map<String, Identifier> converted = new LinkedHashMap<>();
        Map<IngredientKey, String> convertedIngredients = new LinkedHashMap<>();
        manualRecipes.forEach((output, recipe) -> converted.put(RecipeResourceResolver.sortKey(
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(output)))), recipe));
        manualIngredients.forEach((key, item) -> convertedIngredients.put(key,
                IngredientSelectionKey.legacy(item)));
        return plan(catalog, suppliedStock,
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(target))), requested,
                converted, convertedIngredients, maxDepth, maxNodes);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                IStackKey<?> target, long requested,
                                Map<String, Identifier> manualRecipes,
                                Map<IngredientKey, String> manualIngredients,
                                int maxDepth, int maxNodes)
    {
        return plan(catalog, suppliedStock, target, requested, manualRecipes, manualIngredients,
                maxDepth, maxNodes, SEARCH_TIME_LIMIT_NANOS, true);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                IStackKey<?> target, long requested,
                                Map<String, Identifier> manualRecipes,
                                Map<IngredientKey, String> manualIngredients,
                                int maxDepth, int maxNodes, long maxSearchNanos)
    {
        return plan(catalog, suppliedStock, target, requested, manualRecipes, manualIngredients,
                maxDepth, maxNodes, maxSearchNanos, true);
    }

    public static Proposal plan(Catalog catalog, Map<IStackKey<?>, Long> suppliedStock,
                                IStackKey<?> target, long requested,
                                Map<String, Identifier> manualRecipes,
                                Map<IngredientKey, String> manualIngredients,
                                int maxDepth, int maxNodes, long maxSearchNanos,
                                boolean optimalSearch)
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
        ClientPlanningBudget budget = new ClientPlanningBudget(maxNodes, maxSearchNanos, System::nanoTime,
                optimalSearch);
        resolve(target, requested,
                byOutput, new HashSet<>(), state, manualRecipes, manualIngredients,
                0, maxDepth, budget);
        boolean exhausted = budget.exhausted();
        return new Proposal(state.recipes, state.ingredients, state.missing, state.usedStock,
                exhausted, PlanningOutcome.completed(!state.missing.isEmpty(),
                state.rootNoRecipe, state.cyclic, exhausted));
    }

    private static void resolve(IStackKey<?> resource, long needed, Map<IStackKey<?>, List<Recipe>> byOutput,
                                Set<IStackKey<?>> visiting, State state,
                                Map<String, Identifier> manualRecipes,
                                Map<IngredientKey, String> manualIngredients,
                                int depth, int maxDepth, ClientPlanningBudget budget)
    {
        budget.checkCancellation();
        if(!budget.visit(budgetIdentity(resource))){state.missing.merge(resource,needed,SaturatingLongMath::add);return;}
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
            Identifier selected = manualRecipes.get(resourceId);
            if (selected == null) selected = manualRecipes.get(RecipeResourceResolver.sortKey(resource));
            if (selected == null) selected = state.recipes.get(resourceId);
            if (selected != null)
            {
                Identifier choice = selected;
                candidates = candidates.stream().filter(recipe -> recipe.id().equals(choice)).toList();
            }
            if (candidates.isEmpty())
            {
                if (depth == 0) state.rootNoRecipe = true;
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
            State cyclicFallback=null;boolean triedCandidate=false;
            for (Recipe recipe : candidates)
            {
                if(!PlanningBranches.shouldTryCandidate(triedCandidate,budget))break;
                triedCandidate=true;
                State branch = state.copy();
                Identifier previous = branch.recipes.putIfAbsent(resourceId, recipe.id());
                if (previous != null && !previous.equals(recipe.id())) continue;
                State attempted = branch;
                var evaluated=PlanningCycleBranch.evaluateWithStatus(state, () -> {
                    resolveRecipe(resource, remainder, recipe, byOutput, new HashSet<>(visiting), attempted,
                            manualRecipes, manualIngredients, depth, maxDepth, budget);
                    return attempted;
                }, baseline -> rejectCyclicCandidate(baseline, resource, remainder));
                branch=evaluated.state();
                if(evaluated.cyclic()){if(cyclicFallback==null)cyclicFallback=branch;continue;}
                if (best == null || compare(branch, recipe, best, bestRecipe) < 0)
                {
                    best = branch;
                    bestRecipe = recipe;
                }
                if (missingAmount(branch.missing) == 0) break;
            }
            if(best==null&&cyclicFallback!=null)state.replaceWith(cyclicFallback);
            else if (best == null) state.missing.merge(resource, remainder, SaturatingLongMath::add);
            else state.replaceWith(best);
        }
        finally { visiting.remove(resource); }
    }

    private static void resolveRecipe(IStackKey<?> output, long remainder, Recipe recipe,
                                      Map<IStackKey<?>, List<Recipe>> byOutput, Set<IStackKey<?>> visiting,
                                      State state, Map<String, Identifier> manualRecipes,
                                      Map<IngredientKey, String> manualIngredients,
                                      int depth, int maxDepth, ClientPlanningBudget budget)
    {
        List<List<Candidate>> options = new ArrayList<>();
        for (Slot slot : recipe.slots())
        {
            IngredientKey key = new IngredientKey(recipe.id(), slot.index());
            String fixed = manualIngredients.get(key);
            if (fixed == null) fixed = state.ingredients.get(key);
            List<Candidate> candidates;
            if (fixed != null)
            {
                String choice = fixed;
                candidates = slot.candidates().stream().filter(candidate ->
                        choice.equals(candidate.selection())
                                || IngredientSelectionKey.matches(choice, candidate.key())).toList();
                if (candidates.isEmpty()) throw new IllegalArgumentException("invalid ingredient proposal for " + key);
            }
            else
            {
                candidates = slot.candidates().stream().sorted(Comparator
                        .<Candidate>comparingLong(candidate -> available(state.stock, candidate.key())).reversed()
                        .thenComparing(candidate -> !recipesFor(byOutput, candidate.key()).isEmpty() ? 0 : 1)
                        .thenComparing(candidate -> RecipeResourceResolver.resolutionKey(candidate.key()))).toList();
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
        boolean triedCandidate=false;
        for (List<Candidate> variant : SingleSubstitutionVariants.from(options))
        {
            if(!PlanningBranches.shouldTryCandidate(triedCandidate,budget))break;
            triedCandidate=true;
            State branch = state.copy();
            try{if (!applyVariant(output, remainder, recipe, byOutput, visiting, branch, manualRecipes,
                    manualIngredients, depth, maxDepth, budget, variant)) continue;}
            catch(PlanningCycleBranch.Cycle ignored){continue;}
            String key = variant.stream().map(candidate -> RecipeResourceResolver.resolutionKey(candidate.key()))
                    .collect(java.util.stream.Collectors.joining("|"));
            if (best == null || compare(branch, recipe, best, recipe) < 0
                    || compare(branch, recipe, best, recipe) == 0 && key.compareTo(bestKey) < 0)
            {
                best = branch;
                bestKey = key;
            }
            if (missingAmount(branch.missing) == 0) break;
        }
        if(best==null)throw new PlanningCycleBranch.Cycle();
        state.replaceWith(best);
    }

    private static boolean applyVariant(IStackKey<?> output, long remainder, Recipe recipe,
                                        Map<IStackKey<?>, List<Recipe>> byOutput,
                                        Set<IStackKey<?>> visiting, State state,
                                        Map<String, Identifier> manualRecipes,
                                        Map<IngredientKey, String> manualIngredients,
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
                String candidateItem = candidate.selection();
                String previous = state.ingredients.putIfAbsent(key, candidateItem);
                if (previous != null && !previous.equals(candidateItem)) return false;
            }
            boolean selfInput = shape.selfIncrement() && StackKeyMatch.exact(output, candidate.key());
            long inputAmount = selfInput ? candidate.count()
                    : slot.use().requiredAmount(crafts, candidate.key(), candidate.count(),state.stock);
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
        State rejected = baseline.copy();
        rejected.cyclic = true;
        rejected.missing.merge(resource, remainder, SaturatingLongMath::add);
        return rejected;
    }

    public record Catalog(List<Recipe> recipes) { public Catalog { recipes = List.copyOf(recipes); } }

    private static final class CaptureCursor
    {
        private final RecipeHolder<?> holder;
        private final List<KeyAmount> outputs;
        private final List<Recipe> captured;
        private int outputIndex;
        private DirectionCursor direction;
        private CaptureCursor(Level level,RecipeHolder<?> holder){this.holder=holder;this.outputs=RecipeOutputResolver.outputs(holder.value(),level);this.captured=new ArrayList<>(outputs.size());}
        private boolean advance(Level level){if(outputs.isEmpty())return true;if(direction==null)direction=new DirectionCursor(level,holder,outputs.get(outputIndex));Recipe recipe=direction.advance(level);if(recipe==null)return false;captured.add(recipe);direction=null;outputIndex++;return outputIndex>=outputs.size();}
        private List<Recipe> result(){return List.copyOf(captured);}
    }

    private static final class DirectionCursor
    {
        private final RecipeHolder<?> holder;
        private final KeyAmount output;
        private final List<RecipeResourceResolver.ResourceIngredient> ingredients;
        private final List<ItemStack> baselineSamples;
        private final List<Slot> slots=new ArrayList<>();
        private int ingredientIndex;
        private int candidateIndex;
        private LinkedHashMap<String,Candidate> candidates;
        private DirectionCursor(Level level,RecipeHolder<?> holder,KeyAmount output){this.holder=holder;this.output=output;this.ingredients=RecipeResourceResolver.ingredientsForOutput(holder.value(),output.key());List<RecipePlan.IngredientSelection> baseline=ingredients.stream().filter(ingredient->ingredient.candidates().getFirst().key() instanceof ItemStackKey).map(ingredient->new RecipePlan.IngredientSelection(ingredient.slot(),IngredientSelectionKey.exact(ingredient.candidates().getFirst().key()))).toList();this.baselineSamples=SimulatedCrafting.selectedSamples(holder,baseline);}
        private Recipe advance(Level level)
        {
            if(ingredientIndex<ingredients.size())
            {
                RecipeResourceResolver.ResourceIngredient ingredient=ingredients.get(ingredientIndex);
                if(candidates==null)candidates=new LinkedHashMap<>();
                if(candidateIndex<ingredient.candidates().size())
                {
                    KeyAmount value=ingredient.candidates().get(candidateIndex++);Candidate candidate=new Candidate(value.key(),value.amount());
                    if(value.key() instanceof ItemStackKey itemKey){Identifier selectedItem=itemId(value.key());KeyAmount proxy=SimulatedCrafting.bucketFluidInput(holder,level,baselineSamples,ingredient.slot(),itemKey.getReadOnlyStack());if(proxy!=null){Candidate fluid=new Candidate(proxy.key(),proxy.amount(),FluidContainerChoice.proxy(selectedItem));candidates.putIfAbsent(fluid.selection(),fluid);}}
                    candidates.putIfAbsent(candidate.selection(),candidate);return null;
                }
                if(!candidates.isEmpty())slots.add(new Slot(ingredient.slot(),List.copyOf(candidates.values()),VirtualInputUse.CONSUMED));
                ingredientIndex++;candidateIndex=0;candidates=null;return null;
            }
            List<RecipePlan.IngredientSelection> baseline=slots.stream().filter(slot->slot.candidates().getFirst().selectionItem()!=null).map(slot->new RecipePlan.IngredientSelection(slot.index(),slot.candidates().getFirst().selection())).toList();
            boolean[] reusable=SimulatedCrafting.reusableIngredientSlots(holder,level,baseline);
            List<Slot> completedSlots=slots.stream().map(slot->new Slot(slot.index(),slot.candidates(),VirtualInputUse.forRecipeSlot(holder.value(),slot.index(),slot.index()<reusable.length&&reusable[slot.index()]))).toList();
            return new Recipe(holder.id().identifier(),RecipePlanningService.family(holder),output.key(),Math.max(1,output.amount()),RecipeIoProfileRegistry.outputMatchSemantics(holder.value(),holder.id().identifier().toString()),completedSlots);
        }
    }

    public static final class CatalogBuilder
    {
        private Level level;
        private List<RecipeHolder<?>> allHolders;
        private List<RecipeHolder<?>> holders;
        private final Map<Identifier, List<Recipe>> captures = new HashMap<>();
        private final int total;
        private int next;
        private int completed;
        private CaptureCursor cursor;
        private long captureNanos;
        private long captureSteps;
        private long mergeNanos;
        private List<Recipe> ordered;
        private int mergeIndex;
        private Catalog catalog;

        private CatalogBuilder(Level level, List<RecipeHolder<?>> holders)
        {
            this.level = level;
            this.allHolders = List.copyOf(holders);
            this.total = holders.size();
            this.holders = this.allHolders;
            if(this.holders.isEmpty())advanceMerge(Long.MAX_VALUE);
        }
        private CatalogBuilder(Catalog catalog,int total){this.level=null;this.allHolders=List.of();this.holders=List.of();this.total=total;this.completed=total;this.catalog=catalog;}

        public void advance(long timeBudgetNanos)
        {
            if(timeBudgetNanos<1||complete())return;
            int processed = 0;
            long started = System.nanoTime();
            while(next<holders.size()&&(processed<1||System.nanoTime()-started<timeBudgetNanos))
            {
                long captureStarted=System.nanoTime();
                if(cursor==null)cursor=new CaptureCursor(level,holders.get(next));
                boolean holderComplete=cursor.advance(level);
                captureSteps++;
                captureNanos+=System.nanoTime()-captureStarted;
                if(holderComplete){RecipeHolder<?> holder=holders.get(next++);captures.put(holder.id().identifier(),cursor.result());cursor=null;completed++;}
                processed++;
            }
            if(next==holders.size()&&!complete())advanceMerge(Math.max(1L,timeBudgetNanos-(System.nanoTime()-started)));
        }

        private void advanceMerge(long timeBudgetNanos)
        {
            long mergeStarted=System.nanoTime();
            if(ordered==null)ordered=new ArrayList<>();int processed=0;
            while(mergeIndex<allHolders.size()&&(processed<1||System.nanoTime()-mergeStarted<timeBudgetNanos)){RecipeHolder<?> holder=allHolders.get(mergeIndex++);ordered.addAll(captures.getOrDefault(holder.id().identifier(),List.of()));captures.remove(holder.id().identifier());processed++;}
            mergeNanos+=System.nanoTime()-mergeStarted;if(mergeIndex<allHolders.size())return;
            catalog = new Catalog(ordered);
            ordered=null;
            captures.clear();
            allHolders = List.of();
            holders = List.of();
            level = null;
        }

        public boolean complete() { return catalog != null; }
        public int completedRecipes() { return completed; }
        public int totalRecipes() { return total; }
        public long captureMillis(){return captureNanos/1_000_000L;}
        public long captureSteps(){return captureSteps;}
        public long mergeMillis(){return mergeNanos/1_000_000L;}
        public Catalog catalog()
        {
            if (catalog == null) throw new IllegalStateException("recipe catalog is still building");
            return catalog;
        }
    }
    public record Recipe(Identifier id, String family, IStackKey<?> output, long outputCount,
                         RecipeIoProfileRegistry.OutputMatchSemantics outputMatch, List<Slot> slots)
    {
        public Recipe
        {
            Objects.requireNonNull(id); Objects.requireNonNull(family); Objects.requireNonNull(output);
            Objects.requireNonNull(outputMatch);
            if (outputCount < 1) throw new IllegalArgumentException("invalid recipe output");
            slots = List.copyOf(slots);
        }
    }
    public record Slot(int index, List<Candidate> candidates, VirtualInputUse use)
    {
        public Slot
        {
            if (index < 0 || candidates.isEmpty() || use == null) throw new IllegalArgumentException("invalid recipe slot");
            candidates = List.copyOf(candidates);
        }
        public boolean reusable(){return use.sharedReusable();}
    }
    /** Avoid retaining derivable selection strings for millions of ordinary catalog candidates. */
    public static final class Candidate
    {
        private final IStackKey<?> key;private final long count;private final Identifier selectionItemOverride;private final String selectionOverride;
        public Candidate(IStackKey<?> key,long count){this(key,count,null,null,true);}
        public Candidate(IStackKey<?> key,long count,Identifier selectionItem){this(key,count,selectionItem,selectionItem==null?null:selectionItem.toString(),false);}
        public Candidate(IStackKey<?> key,long count,Identifier selectionItem,String selection){this(key,count,java.util.Objects.equals(selectionItem,key instanceof ItemStackKey?itemId(key):null)&&IngredientSelectionKey.exact(key).equals(selection)?null:selectionItem,IngredientSelectionKey.exact(key).equals(selection)?null:selection,true);}
        private Candidate(IStackKey<?> key,long count,Identifier selectionItemOverride,String selectionOverride,boolean normalized){if(key==null||key.isEmpty()||count<1)throw new IllegalArgumentException("invalid candidate");if(!normalized&&(selectionOverride==null||selectionOverride.isBlank()))throw new IllegalArgumentException("invalid candidate");this.key=key;this.count=count;this.selectionItemOverride=selectionItemOverride;this.selectionOverride=selectionOverride;}
        public IStackKey<?> key(){return key;}public long count(){return count;}
        public Identifier selectionItem(){return selectionItemOverride!=null?selectionItemOverride:key instanceof ItemStackKey?itemId(key):null;}
        public String selection(){return selectionOverride!=null?selectionOverride:IngredientSelectionKey.exact(key);}
        public Identifier explicitSelectionItem(){return selectionItemOverride;}public String explicitSelection(){return selectionOverride;}
    }
    public record IngredientKey(Identifier recipe, int slot) {}
    public record Proposal(Map<String, Identifier> recipes,
                           Map<IngredientKey, String> ingredients,
                           Map<IStackKey<?>, Long> missing,
                           Map<IStackKey<?>, Long> extraction,
                           boolean searchExhausted, PlanningOutcome outcome)
    {
        public Proposal
        {
            recipes = Map.copyOf(recipes); ingredients = Map.copyOf(ingredients);
            missing = Map.copyOf(missing); extraction = Map.copyOf(extraction);
            Objects.requireNonNull(outcome);
        }
        public boolean craftable() { return missing.isEmpty(); }
    }

    private static final class State
    {
        private MatchingStock<IStackKey<?>, Identifier> stock;
        private Map<IStackKey<?>, Long> missing;
        private Map<IStackKey<?>, Long> reusableRequirements;
        private Map<IStackKey<?>, Long> usedStock;
        private int steps;
        private Map<String, Identifier> recipes;
        private Map<IngredientKey, String> ingredients;
        private boolean rootNoRecipe;
        private boolean cyclic;
        private State(MatchingStock<IStackKey<?>, Identifier> stock,
                      Map<IStackKey<?>, Long> missing,
                      Map<IStackKey<?>, Long> reusableRequirements,
                      Map<IStackKey<?>, Long> usedStock, int steps,
                      Map<String, Identifier> recipes,
                      Map<IngredientKey, String> ingredients)
        { this.stock = stock; this.missing = missing; this.reusableRequirements = reusableRequirements;
            this.usedStock = usedStock;
            this.steps = steps; this.recipes = recipes; this.ingredients = ingredients; }
        private State copy()
        { State result = new State(stock.copy(), new LinkedHashMap<>(missing),
                new LinkedHashMap<>(reusableRequirements), new LinkedHashMap<>(usedStock), steps,
                new LinkedHashMap<>(recipes), new LinkedHashMap<>(ingredients));
            result.rootNoRecipe = rootNoRecipe; result.cyclic = cyclic; return result; }
        private void replaceWith(State state)
        { stock = state.stock; missing = state.missing; reusableRequirements = state.reusableRequirements;
            usedStock = state.usedStock;
            steps = state.steps; recipes = state.recipes; ingredients = state.ingredients;
            rootNoRecipe = state.rootNoRecipe; cyclic = state.cyclic; }
    }

    private static Identifier itemId(IStackKey<?> key)
    { return BuiltInRegistries.ITEM.getKey(((ItemStackKey) key).getSource()); }

    private static String budgetIdentity(IStackKey<?> key)
    {
        return RecipeResourceResolver.resolutionKey(key);
    }

    static List<Recipe> recipesFor(Map<IStackKey<?>, List<Recipe>> byOutput, IStackKey<?> resource)
    {
        List<Recipe> exact = SymmetricMapLookup.first(byOutput, resource, StackKeyMatch::exact);
        if (!exact.isEmpty()) return exact;
        for (var entry : byOutput.entrySet())
        {
            List<Recipe> configured = entry.getValue().stream().filter(recipe ->
                    RecipeIoProfileRegistry.outputMatches(recipe.outputMatch(), resource, entry.getKey(),
                            StackKeyMatch::exact,
                            (left, right) -> left.isSame(right) || right.isSame(left))).toList();
            if (!configured.isEmpty()) return configured;
        }
        List<String> sameItemCandidates = byOutput.entrySet().stream()
                .filter(entry -> resource.isSame(entry.getKey()) || entry.getKey().isSame(resource))
                .limit(16).map(entry -> OrderDiagnostics.resource(entry.getKey()) + "="
                        + entry.getValue().stream().map(recipe -> recipe.id().toString()).toList()).toList();
        if (!sameItemCandidates.isEmpty()) OrderDiagnostics.LOGGER.warn(
                "{} client dependency exact miss requested={} sameItemCandidates={}",
                OrderDiagnostics.PREFIX, OrderDiagnostics.resource(resource), sameItemCandidates);
        return List.of();
    }

    private static long available(MatchingStock<IStackKey<?>, Identifier> stock,
                                  IStackKey<?> requested)
    { return stock.available(requested.getTypeId(), key -> StackKeyMatch.exact(requested, key)); }

    private static long consume(State state, IStackKey<?> requested, long amount)
    {
        return state.stock.consume(requested.getTypeId(), key -> StackKeyMatch.exact(requested, key), amount,
                (key, used) -> state.usedStock.merge(key, used, SaturatingLongMath::add));
    }
}
