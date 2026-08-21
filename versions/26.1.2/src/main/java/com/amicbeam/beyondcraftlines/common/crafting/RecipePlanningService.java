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

    private RecipePlanningService() {}

    public static List<RecipeHolder<?>> visibleRecipes(Level level)
    {
        Object catalog = level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess() : RecipeCatalog.class;
        return VISIBLE_RECIPE_CACHE.computeIfAbsent(catalog, ignored -> RecipeCatalog.forLevel(level).stream()
                .filter(RecipePlanningService::supported)
                .filter(holder -> !RecipeOutputResolver.outputs(
                        holder.value(), level).isEmpty())
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
                .toList());
    }

    public static void clearRecipeCache()
    {
        VISIBLE_RECIPE_CACHE.clear();
        RecipeIngredientResolver.clearCache();
        PlanningSnapshotService.clearRecipeEpochCache();
        ClientRecipePlanner.clearCache();
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
                availableFamilies, overrides);
    }

    public static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                  PlanningSnapshotService.Snapshot suppliedStock,
                                  Set<String> availableFamilies, RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        for (PlanningSnapshotService.ComponentEntry entry : suppliedStock.componentEntries())
            exact.merge(entry.key(), entry.amount(), SaturatingLongMath::add);
        return plan(level, target, amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides);
    }

    public static RecipePlan plan(ServerLevel level, Identifier target, long amount,
                                  Map<Identifier, Long> suppliedStock, Set<String> availableFamilies,
                                  RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        suppliedStock.forEach((item, count) -> exact.put(
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(item))), count));
        return plan(level, key(target), amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides);
    }

    public static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                  Map<Identifier, Long> suppliedStock, Set<String> availableFamilies,
                                  RecipeResolutionOverrides overrides)
    {
        LinkedHashMap<IStackKey<?>, Long> exact = new LinkedHashMap<>();
        suppliedStock.forEach((item, count) -> exact.put(key(item), count));
        return plan(level, target, amount, new MatchingStock<>(IStackKey::getTypeId, exact),
                availableFamilies, overrides);
    }

    private static RecipePlan plan(ServerLevel level, IStackKey<?> target, long amount,
                                   MatchingStock<IStackKey<?>, Identifier> stock,
                                   Set<String> availableFamilies, RecipeResolutionOverrides overrides)
    {

        // The requested amount is a manufacturing quantity, not a desired final stock level.
        // Existing target items are shown to the player but never satisfy this order.
        stock.consume(target.getTypeId(), target::isSame, Long.MAX_VALUE);

        Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : visibleRecipes(level))
        {
            String family = family(holder);
            if (!"crafting".equals(family) && !availableFamilies.contains(family)) continue;
            for (KeyAmount output : RecipeOutputResolver.outputs(holder.value(), level))
                byOutput.computeIfAbsent(output.key(), ignored -> new ArrayList<>()).add(holder);
        }
        PlanningState state = new PlanningState(stock, new ArrayList<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>());
        int maxDepth = CraftlinesConfig.MAX_PLANNING_DEPTH.get();
        resolve(level, target, amount, null, byOutput, new HashSet<>(), state, overrides, 0, maxDepth,
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
                                RecipeResolutionOverrides overrides, int depth, int maxDepth,
                                PlanningBudget budget)
    {
        budget.enterNode();
        long used = state.stock.consume(resource.getTypeId(), key -> resource.isSame(key)
                        && (requiredIngredient == null || key instanceof ItemStackKey itemKey
                        && requiredIngredient.test(itemKey.getReadOnlyStack())), needed,
                (key, amount) -> state.usedStock.merge(key, amount, SaturatingLongMath::add));
        long remainder = needed - used;
        if (remainder == 0) return;
        if (depth >= maxDepth)
        {
            state.missing.merge(resource, remainder, SaturatingLongMath::add);
            return;
        }
        String resourceId = RecipeResourceResolver.sortKey(resource);
        if (!visiting.add(resourceId))
        {
            state.missing.merge(resource, remainder, SaturatingLongMath::add);
            return;
        }

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
            if (candidates.isEmpty())
            {
                state.missing.merge(resource, remainder, SaturatingLongMath::add);
                return;
            }

            if (!PlanningBranches.recipesRequireBranches(candidates.size()))
            {
                resolveRecipe(level, resource, remainder, candidates.getFirst(), byOutput,
                        new HashSet<>(visiting), state, overrides, depth, maxDepth, budget);
                return;
            }
            PlanningState best = null;
            Identifier bestRecipe = null;
            for (RecipeHolder<?> holder : candidates)
            {
                PlanningState branch = state.copy();
                resolveRecipe(level, resource, remainder, holder, byOutput, new HashSet<>(visiting), branch,
                        overrides, depth, maxDepth, budget);
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
                                      RecipeResolutionOverrides overrides, int depth, int maxDepth,
                                      PlanningBudget budget)
    {
        List<Integer> slots = new ArrayList<>();
        List<List<KeyAmount>> options = new ArrayList<>();
        List<RecipeResourceResolver.ResourceIngredient> recipeIngredients =
                RecipeResourceResolver.ingredients(holder.value());
        for (RecipeResourceResolver.ResourceIngredient ingredient : recipeIngredients)
        {
            int currentIndex = ingredient.slot();
            List<KeyAmount> choices = ingredientChoices(holder.id().identifier(), currentIndex, ingredient, state.stock,
                    byOutput, overrides, budget);
            if (choices.isEmpty())
                throw new IllegalArgumentException("ingredient cannot be enumerated for " + holder.id()
                        + " slot " + currentIndex);
            slots.add(currentIndex);
            options.add(choices);
        }

        if (!PlanningBranches.ingredientsRequireBranches(options))
        {
            resolveRecipeVariant(level, outputKey, remainder, holder, byOutput, visiting, state,
                    overrides, depth, maxDepth, budget, options.stream().map(List::getFirst).toList());
            return;
        }

        PlanningState best = null;
        String bestSelectionKey = null;
        for (List<KeyAmount> variant : SingleSubstitutionVariants.from(
                options, (left, right) -> left.key().isSameTypeSameComponents(right.key())))
        {
            budget.enterNode();
            PlanningState branch = state.copy();
            resolveRecipeVariant(level, outputKey, remainder, holder, byOutput, visiting, branch,
                    overrides, depth, maxDepth, budget, variant);
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
                                             RecipeResolutionOverrides overrides, int depth, int maxDepth,
                                             PlanningBudget budget,
                                             List<KeyAmount> variant)
    {
        KeyAmount result = RecipeOutputResolver.outputs(holder.value(), level).stream()
                .filter(value -> outputKey.isSame(value.key())).findFirst().orElseThrow();
        long perCraft = Math.max(1, result.amount());
        long crafts = SaturatingLongMath.ceilDiv(remainder, perCraft);
        List<RecipePlan.Material> inputs = new ArrayList<>();
        List<RecipeResourceResolver.ResourceIngredient> recipeIngredients =
                RecipeResourceResolver.ingredients(holder.value());
        List<RecipePlan.IngredientSelection> selections = new ArrayList<>();
        for (int i = 0; i < variant.size(); i++)
            if (variant.get(i).key() instanceof ItemStackKey itemKey)
                selections.add(new RecipePlan.IngredientSelection(recipeIngredients.get(i).slot(),
                        BuiltInRegistries.ITEM.getKey(itemKey.getSource())));
        boolean[] reusableSlots = SimulatedCrafting.reusableIngredientSlots(holder, level, selections);
        for (int i = 0; i < variant.size(); i++)
        {
            RecipeResourceResolver.ResourceIngredient ingredient = recipeIngredients.get(i);
            KeyAmount choice = variant.get(i);
            boolean reusable = ingredient.slot() < reusableSlots.length && reusableSlots[ingredient.slot()];
            long inputAmount = reusable ? choice.amount()
                    : SaturatingLongMath.multiply(crafts, choice.amount());
            inputs.add(new RecipePlan.Material(choice.key(), inputAmount, ingredient.slot()));
            resolve(level, choice.key(), inputAmount, ingredient.itemIngredient(), byOutput,
                    visiting, state, overrides, depth + 1, maxDepth, budget);
        }
        state.steps.add(new RecipePlan.Step(holder.id().identifier(), family(holder), outputKey,
                perCraft, crafts, inputs, selections));
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

    private static List<KeyAmount> ingredientChoices(Identifier recipe, int slot,
                                                     RecipeResourceResolver.ResourceIngredient ingredient,
                                                     MatchingStock<IStackKey<?>, Identifier> stock,
                                                     Map<IStackKey<?>, List<RecipeHolder<?>>> byOutput,
                                                     RecipeResolutionOverrides overrides,
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
        Comparator<KeyAmount> comparator = Comparator.<KeyAmount>comparingLong(value -> stock.available(
                        value.key().getTypeId(), value.key()::isSame)).reversed()
                .thenComparing(value -> value.key() instanceof ItemStackKey item
                        && !recipesFor(byOutput, value.key()).isEmpty() ? 0 : 1)
                .thenComparing(value -> RecipeResourceResolver.sortKey(value.key()));
        List<KeyAmount> choices = ingredient.candidates();
        for (int i = 0; i < choices.size(); i++)
            if ((i & 255) == 0) budget.checkTime();
        LinkedHashMap<IStackKey<?>, KeyAmount> unique = new LinkedHashMap<>();
        choices.stream().sorted(comparator).forEach(choice -> unique.putIfAbsent(choice.key(), choice));
        return List.copyOf(unique.values());
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

        private PlanningState(MatchingStock<IStackKey<?>, Identifier> stock, List<RecipePlan.Step> steps,
                              Map<IStackKey<?>, Long> missing, Map<IStackKey<?>, Long> usedStock)
        {
            this.stock = stock;
            this.steps = steps;
            this.missing = missing;
            this.usedStock = usedStock;
        }

        private PlanningState copy()
        { return new PlanningState(stock.copy(), new ArrayList<>(steps), new LinkedHashMap<>(missing),
                new LinkedHashMap<>(usedStock)); }

        private void replaceWith(PlanningState selected)
        {
            stock = selected.stock;
            steps = selected.steps;
            missing = selected.missing;
            usedStock = selected.usedStock;
        }
    }

    public static boolean supported(RecipeHolder<?> holder)
    {
        var recipe = holder.value();
        // Recipe#isIncomplete treats an empty vanilla getIngredients() list as invalid.
        // Many third-party machine recipes intentionally keep that list empty and expose
        // their inputs through their own API, so only apply the vanilla check when the
        // recipe actually uses the vanilla ingredient list.
        if (!recipe.isSpecial() && recipe.placementInfo().isImpossibleToPlace()) return false;
        return !RecipeResourceResolver.ingredients(recipe).isEmpty();
    }

    public static String family(RecipeHolder<?> holder)
    {
        RecipeType<?> type = holder.value().getType();
        String byType = family(type);
        if (byType != null) return byType;
        Identifier serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
        return serializer == null ? type.toString() : serializer.toString();
    }

    public static String family(RecipeType<?> type)
    {
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
