package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
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

    private RecipePlanningService() {}

    public static List<RecipeHolder<?>> visibleRecipes(Level level)
    {
        return VISIBLE_RECIPE_CACHE.computeIfAbsent(level.getRecipeManager(), manager -> manager.getOrderedRecipes().stream()
                .filter(RecipePlanningService::supported)
                .filter(holder -> {
                    ItemStack result = holder.value().getResultItem(level.registryAccess());
                    return !result.isEmpty();
                })
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList());
    }

    public static void clearRecipeCache()
    {
        VISIBLE_RECIPE_CACHE.clear();
        PlanningSnapshotService.clearRecipeEpochCache();
    }

    public static RecipePlan plan(ServerLevel level, int networkId, ResourceLocation target, long amount)
    {
        return plan(level, networkId, target, amount, RecipeResolutionOverrides.EMPTY);
    }

    public static RecipePlan plan(ServerLevel level, int networkId, ResourceLocation target, long amount,
                                  RecipeResolutionOverrides overrides)
    {
        Map<ResourceLocation, Long> stock = PlanningSnapshotService.capture(networkId).asMap();
        Set<String> availableFamilies = DeviceBindingRegistry.availableFamilies(level.getServer(), networkId);
        return plan(level, target, amount, stock, availableFamilies, overrides);
    }

    public static RecipePlan plan(ServerLevel level, ResourceLocation target, long amount,
                                  Map<ResourceLocation, Long> suppliedStock, Set<String> availableFamilies,
                                  RecipeResolutionOverrides overrides)
    {
        Map<ResourceLocation, Long> stock = new HashMap<>(suppliedStock);

        // The requested amount is a manufacturing quantity, not a desired final stock level.
        // Existing target items are shown to the player but never satisfy this order.
        stock.put(target, 0L);

        Map<ResourceLocation, List<RecipeHolder<?>>> byOutput = new HashMap<>();
        for (RecipeHolder<?> holder : visibleRecipes(level))
        {
            String family = family(holder);
            if (!"crafting".equals(family) && !availableFamilies.contains(family)) continue;
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            byOutput.computeIfAbsent(BuiltInRegistries.ITEM.getKey(output.getItem()), ignored -> new ArrayList<>())
                    .add(holder);
        }
        PlanningState state = new PlanningState(stock, new ArrayList<>(), new LinkedHashMap<>());
        int maxDepth = CraftlinesConfig.MAX_PLANNING_DEPTH.get();
        resolve(level, target, amount, byOutput, new HashSet<>(), state, overrides, 0, maxDepth,
                new PlanningBudget(CraftlinesConfig.MAX_PLANNING_NODES.get(),
                        CraftlinesConfig.MAX_PLANNING_TIME_MILLIS.get() * 1_000_000L));
        return new RecipePlan(target, amount, state.steps, state.missing.entrySet().stream()
                .map(entry -> new RecipePlan.Material(entry.getKey(), entry.getValue())).toList());
    }

    private static void resolve(ServerLevel level, ResourceLocation item, long needed,
                                Map<ResourceLocation, List<RecipeHolder<?>>> byOutput,
                                Set<ResourceLocation> visiting, PlanningState state,
                                RecipeResolutionOverrides overrides, int depth, int maxDepth,
                                PlanningBudget budget)
    {
        budget.enterNode();
        long available = state.stock.getOrDefault(item, 0L);
        long used = Math.min(available, needed);
        if (used > 0) state.stock.put(item, available - used);
        long remainder = needed - used;
        if (remainder == 0) return;
        if (depth >= maxDepth)
        {
            state.missing.merge(item, remainder, SaturatingLongMath::add);
            return;
        }
        if (!visiting.add(item))
        {
            state.missing.merge(item, remainder, SaturatingLongMath::add);
            return;
        }

        try
        {
            List<RecipeHolder<?>> candidates = byOutput.getOrDefault(item, List.of());
            ResourceLocation selectedRecipe = overrides.recipeFor(item);
            if (selectedRecipe != null)
            {
                candidates = candidates.stream().filter(holder -> holder.id().equals(selectedRecipe)).toList();
                if (candidates.isEmpty())
                    throw new IllegalArgumentException("selected recipe is unavailable for " + item + ": " + selectedRecipe);
            }
            if (candidates.isEmpty())
            {
                state.missing.merge(item, remainder, SaturatingLongMath::add);
                return;
            }

            PlanningState best = null;
            ResourceLocation bestRecipe = null;
            for (RecipeHolder<?> holder : candidates)
            {
                PlanningState branch = state.copy();
                resolveRecipe(level, item, remainder, holder, byOutput, new HashSet<>(visiting), branch,
                        overrides, depth, maxDepth, budget);
                if (best == null || compare(branch, holder.id(), best, bestRecipe) < 0)
                {
                    best = branch;
                    bestRecipe = holder.id();
                }
            }
            state.replaceWith(Objects.requireNonNull(best));
        }
        finally { visiting.remove(item); }
    }

    private static void resolveRecipe(ServerLevel level, ResourceLocation item, long remainder,
                                      RecipeHolder<?> holder,
                                      Map<ResourceLocation, List<RecipeHolder<?>>> byOutput,
                                      Set<ResourceLocation> visiting, PlanningState state,
                                      RecipeResolutionOverrides overrides, int depth, int maxDepth,
                                      PlanningBudget budget)
    {
        List<Integer> slots = new ArrayList<>();
        List<List<ItemStack>> options = new ArrayList<>();
        int ingredientIndex = 0;
        for (Ingredient ingredient : holder.value().getIngredients())
        {
            int currentIndex = ingredientIndex++;
            if (ingredient.isEmpty()) continue;
            List<ItemStack> choices = ingredientChoices(holder.id(), currentIndex, ingredient, state.stock,
                    byOutput, overrides, budget);
            if (choices.isEmpty())
                throw new IllegalArgumentException("ingredient cannot be enumerated for " + holder.id()
                        + " slot " + currentIndex);
            slots.add(currentIndex);
            options.add(choices);
        }

        PlanningState best = null;
        String bestSelectionKey = null;
        for (List<ItemStack> variant : SingleSubstitutionVariants.from(options))
        {
            budget.enterNode();
            List<RecipePlan.IngredientSelection> selections = new ArrayList<>(variant.size());
            for (int i = 0; i < variant.size(); i++) selections.add(new RecipePlan.IngredientSelection(
                    slots.get(i), BuiltInRegistries.ITEM.getKey(variant.get(i).getItem())));
            PlanningState branch = state.copy();
            resolveRecipeVariant(level, item, remainder, holder, byOutput, visiting, branch,
                    overrides, depth, maxDepth, budget, selections);
            String selectionKey = selections.stream().map(selection -> selection.item().toString())
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

    private static void resolveRecipeVariant(ServerLevel level, ResourceLocation item, long remainder,
                                             RecipeHolder<?> holder,
                                             Map<ResourceLocation, List<RecipeHolder<?>>> byOutput,
                                             Set<ResourceLocation> visiting, PlanningState state,
                                             RecipeResolutionOverrides overrides, int depth, int maxDepth,
                                             PlanningBudget budget,
                                             List<RecipePlan.IngredientSelection> selections)
    {
        ItemStack result = holder.value().getResultItem(level.registryAccess());
        long perCraft = Math.max(1, result.getCount());
        long crafts = SaturatingLongMath.ceilDiv(remainder, perCraft);
        Map<ResourceLocation, Long> inputAmounts = new LinkedHashMap<>();
        boolean[] reusableSlots = SimulatedCrafting.reusableIngredientSlots(holder, level, selections);
        for (RecipePlan.IngredientSelection selection : selections)
        {
            ItemStack choice = holder.value().getIngredients().get(selection.slot()).getItems()[0];
            for (ItemStack candidate : holder.value().getIngredients().get(selection.slot()).getItems())
                if (BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selection.item()))
                { choice = candidate; break; }
            long inputAmount = reusableSlots[selection.slot()] ? Math.max(1, choice.getCount())
                    : SaturatingLongMath.multiply(crafts, Math.max(1, choice.getCount()));
            inputAmounts.merge(selection.item(), inputAmount, SaturatingLongMath::add);
        }
        List<RecipePlan.Material> inputs = inputAmounts.entrySet().stream()
                .map(entry -> new RecipePlan.Material(entry.getKey(), entry.getValue())).toList();
        for (RecipePlan.Material input : inputs)
            resolve(level, input.item(), input.amount(), byOutput, visiting, state, overrides,
                    depth + 1, maxDepth, budget);
        state.steps.add(new RecipePlan.Step(holder.id(), family(holder), item, perCraft, crafts, inputs, selections));
        long produced = SaturatingLongMath.multiply(perCraft, crafts);
        long surplus = produced > remainder ? produced - remainder : 0;
        if (surplus > 0) state.stock.merge(item, surplus, SaturatingLongMath::add);
    }

    private static List<ItemStack> ingredientChoices(ResourceLocation recipe, int slot, Ingredient ingredient,
                                                     Map<ResourceLocation, Long> stock,
                                                     Map<ResourceLocation, List<RecipeHolder<?>>> byOutput,
                                                     RecipeResolutionOverrides overrides,
                                                     PlanningBudget budget)
    {
        ResourceLocation selected = overrides.ingredientFor(recipe, slot);
        if (selected != null)
        {
            for (ItemStack choice : ingredient.getItems())
                if (BuiltInRegistries.ITEM.getKey(choice.getItem()).equals(selected)) return List.of(choice);
            throw new IllegalArgumentException("selected ingredient is invalid for " + recipe
                    + " slot " + slot + ": " + selected);
        }
        Comparator<ItemStack> comparator = Comparator.<ItemStack>comparingLong(stack -> stock.getOrDefault(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), 0L)).reversed()
                .thenComparing(stack -> byOutput.containsKey(
                        BuiltInRegistries.ITEM.getKey(stack.getItem())) ? 0 : 1)
                .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        ItemStack[] choices = ingredient.getItems();
        for (int i = 0; i < choices.length; i++)
            if ((i & 255) == 0) budget.checkTime();
        LinkedHashMap<ResourceLocation, ItemStack> unique = new LinkedHashMap<>();
        Arrays.stream(choices).sorted(comparator).forEach(choice -> unique.putIfAbsent(
                BuiltInRegistries.ITEM.getKey(choice.getItem()), choice));
        return List.copyOf(unique.values());
    }

    private static int compare(PlanningState left, ResourceLocation leftRecipe,
                               PlanningState right, ResourceLocation rightRecipe)
    {
        return new PlanningCandidateRank(missingAmount(left.missing), left.steps.size(), leftRecipe.toString())
                .compareTo(new PlanningCandidateRank(
                        missingAmount(right.missing), right.steps.size(), rightRecipe.toString()));
    }

    private static long missingAmount(Map<ResourceLocation, Long> missing)
    {
        long total = 0;
        for (long amount : missing.values()) total = SaturatingLongMath.add(total, amount);
        return total;
    }

    private static final class PlanningState
    {
        private Map<ResourceLocation, Long> stock;
        private List<RecipePlan.Step> steps;
        private Map<ResourceLocation, Long> missing;

        private PlanningState(Map<ResourceLocation, Long> stock, List<RecipePlan.Step> steps,
                              Map<ResourceLocation, Long> missing)
        {
            this.stock = stock;
            this.steps = steps;
            this.missing = missing;
        }

        private PlanningState copy()
        { return new PlanningState(new HashMap<>(stock), new ArrayList<>(steps), new LinkedHashMap<>(missing)); }

        private void replaceWith(PlanningState selected)
        {
            stock = selected.stock;
            steps = selected.steps;
            missing = selected.missing;
        }
    }

    public static boolean supported(RecipeHolder<?> holder)
    {
        var recipe = holder.value();
        return !recipe.isIncomplete() && !recipe.getIngredients().isEmpty();
    }

    public static String family(RecipeHolder<?> holder)
    {
        RecipeType<?> type = holder.value().getType();
        String byType = family(type);
        if (byType != null) return byType;
        ResourceLocation serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
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
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id == null ? null : id.toString();
    }
}
