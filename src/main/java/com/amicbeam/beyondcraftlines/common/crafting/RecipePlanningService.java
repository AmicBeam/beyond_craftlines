package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.data.DeviceBindingRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
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

    public static void clearRecipeCache() { VISIBLE_RECIPE_CACHE.clear(); }

    public static RecipePlan plan(ServerLevel level, int networkId, ResourceLocation target, long amount)
    {
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        if (network == null) throw new IllegalArgumentException("network not found");
        Map<ResourceLocation, Long> stock = new HashMap<>();
        for (var stored : network.getUnifiedStorage().getStorage())
        {
            if (stored.key() instanceof ItemStackKey key)
                stock.merge(BuiltInRegistries.ITEM.getKey(key.getSource()), stored.amount(),
                        SaturatingLongMath::add);
        }

        // The requested amount is a manufacturing quantity, not a desired final stock level.
        // Existing target items are shown to the player but never satisfy this order.
        stock.put(target, 0L);

        Map<ResourceLocation, List<RecipeHolder<?>>> byOutput = new HashMap<>();
        Set<String> availableFamilies = DeviceBindingRegistry.availableFamilies(level.getServer(), networkId);
        for (RecipeHolder<?> holder : visibleRecipes(level))
        {
            String family = family(holder);
            if (!"crafting".equals(family) && !availableFamilies.contains(family)) continue;
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            byOutput.computeIfAbsent(BuiltInRegistries.ITEM.getKey(output.getItem()), ignored -> new ArrayList<>())
                    .add(holder);
        }
        List<RecipePlan.Step> steps = new ArrayList<>();
        Map<ResourceLocation, Long> missing = new LinkedHashMap<>();
        int maxDepth = CraftlinesConfig.MAX_PLANNING_DEPTH.get();
        resolve(level, target, amount, stock, byOutput, new HashSet<>(), steps, missing, 0, maxDepth,
                new PlanningBudget(CraftlinesConfig.MAX_PLANNING_NODES.get(),
                        CraftlinesConfig.MAX_PLANNING_TIME_MILLIS.get() * 1_000_000L));
        return new RecipePlan(target, amount, steps, missing.entrySet().stream()
                .map(entry -> new RecipePlan.Material(entry.getKey(), entry.getValue())).toList());
    }

    private static void resolve(ServerLevel level, ResourceLocation item, long needed,
                                Map<ResourceLocation, Long> stock,
                                Map<ResourceLocation, List<RecipeHolder<?>>> byOutput,
                                Set<ResourceLocation> visiting, List<RecipePlan.Step> steps,
                                Map<ResourceLocation, Long> missing, int depth, int maxDepth,
                                PlanningBudget budget)
    {
        budget.enterNode();
        long available = stock.getOrDefault(item, 0L);
        long used = Math.min(available, needed);
        if (used > 0) stock.put(item, available - used);
        long remainder = needed - used;
        if (remainder == 0) return;
        if (depth >= maxDepth || !visiting.add(item))
        {
            missing.merge(item, remainder, SaturatingLongMath::add);
            return;
        }

        RecipeHolder<?> holder = byOutput.getOrDefault(item, List.of()).stream().findFirst().orElse(null);
        if (holder == null)
        {
            visiting.remove(item);
            missing.merge(item, remainder, SaturatingLongMath::add);
            return;
        }
        ItemStack result = holder.value().getResultItem(level.registryAccess());
        long perCraft = Math.max(1, result.getCount());
        long crafts = SaturatingLongMath.ceilDiv(remainder, perCraft);
        Map<ResourceLocation, Long> inputAmounts = new LinkedHashMap<>();
        boolean[] reusableSlots = SimulatedCrafting.reusableIngredientSlots(holder, level);
        int ingredientIndex = 0;
        for (Ingredient ingredient : holder.value().getIngredients())
        {
            int currentIndex = ingredientIndex++;
            if (ingredient.isEmpty()) continue;
            ItemStack choice = chooseIngredient(ingredient, stock, byOutput, budget);
            if (choice == null || choice.isEmpty()) continue;
            ResourceLocation choiceId = BuiltInRegistries.ITEM.getKey(choice.getItem());
            long inputAmount = reusableSlots[currentIndex] ? Math.max(1, choice.getCount())
                    : SaturatingLongMath.multiply(crafts, Math.max(1, choice.getCount()));
            inputAmounts.merge(choiceId, inputAmount, SaturatingLongMath::add);
        }
        List<RecipePlan.Material> inputs = inputAmounts.entrySet().stream()
                .map(entry -> new RecipePlan.Material(entry.getKey(), entry.getValue())).toList();
        for (RecipePlan.Material input : inputs)
            resolve(level, input.item(), input.amount(), stock, byOutput, visiting, steps, missing,
                    depth + 1, maxDepth, budget);
        visiting.remove(item);
        steps.add(new RecipePlan.Step(holder.id(), family(holder), item, perCraft, crafts, inputs));
        long produced = SaturatingLongMath.multiply(perCraft, crafts);
        long surplus = produced > remainder ? produced - remainder : 0;
        if (surplus > 0) stock.merge(item, surplus, SaturatingLongMath::add);
    }

    private static ItemStack chooseIngredient(Ingredient ingredient, Map<ResourceLocation, Long> stock,
                                              Map<ResourceLocation, List<RecipeHolder<?>>> byOutput,
                                              PlanningBudget budget)
    {
        Comparator<ItemStack> comparator = Comparator.<ItemStack>comparingLong(stack -> stock.getOrDefault(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), 0L)).reversed()
                .thenComparing(stack -> byOutput.containsKey(
                        BuiltInRegistries.ITEM.getKey(stack.getItem())) ? 0 : 1)
                .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        ItemStack best = null;
        ItemStack[] choices = ingredient.getItems();
        for (int i = 0; i < choices.length; i++)
        {
            if ((i & 255) == 0) budget.checkTime();
            if (best == null || comparator.compare(choices[i], best) < 0) best = choices[i];
        }
        return best;
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
