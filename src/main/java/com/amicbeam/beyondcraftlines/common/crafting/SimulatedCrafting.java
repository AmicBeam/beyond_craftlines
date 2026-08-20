package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes one real crafting-table operation against network stacks, including dynamic output and remainders. */
public final class SimulatedCrafting
{
    private SimulatedCrafting() {}

    public static Attempt craftOne(ServerLevel level, UnifiedStorage storage, ResourceLocation recipeId,
                                   ResourceLocation expectedOutput)
    {
        return craftBatch(level, storage, recipeId, expectedOutput, 1, List.of());
    }

    public static Attempt craftBatch(ServerLevel level, UnifiedStorage storage, ResourceLocation recipeId,
                                     ResourceLocation expectedOutput, long requestedCrafts,
                                     List<RecipePlan.IngredientSelection> selections)
    { return craftBatch(level, storage, recipeId, expectedOutput, requestedCrafts, selections, List.of()); }

    public static Attempt craftBatch(ServerLevel level, UnifiedStorage storage, ResourceLocation recipeId,
                                     ResourceLocation expectedOutput, long requestedCrafts,
                                     List<RecipePlan.IngredientSelection> selections,
                                     List<RecipePlan.ReservedMaterial> orderReserved)
    { return craftBatch(level, storage, recipeId, expectedOutput, requestedCrafts,
            selections, orderReserved, false); }

    public static Attempt craftBatch(ServerLevel level, UnifiedStorage storage, ResourceLocation recipeId,
                                     ResourceLocation expectedOutput, long requestedCrafts,
                                     List<RecipePlan.IngredientSelection> selections,
                                     List<RecipePlan.ReservedMaterial> orderReserved,
                                     boolean escrowOutput)
    { return craftBatch(level, storage, recipeId, expectedOutput, requestedCrafts, selections,
            orderReserved, escrowOutput, null); }

    public static Attempt craftBatch(ServerLevel level, UnifiedStorage storage, ResourceLocation recipeId,
                                     ResourceLocation expectedOutput, long requestedCrafts,
                                     List<RecipePlan.IngredientSelection> selections,
                                     List<RecipePlan.ReservedMaterial> orderReserved,
                                     boolean escrowOutput, PlanningSnapshotService.Snapshot networkSnapshot)
    {
        if (requestedCrafts < 1) return Attempt.failed("invalid crafting batch size");
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe))
            return Attempt.failed("crafting recipe is no longer available: " + recipeId);

        Map<ItemStackKey, Long> reservedAmounts = reservedAmounts(orderReserved);
        Prepared prepared = prepare(storage, recipe, level, selections, reservedAmounts, networkSnapshot);
        if (prepared == null) return Attempt.failed("waiting for matching crafting ingredients");

        ItemStack output;
        NonNullList<ItemStack> remainders;
        try
        {
            output = recipe.assemble(prepared.input(), level.registryAccess());
            remainders = recipe.getRemainingItems(prepared.input());
        }
        catch (RuntimeException exception)
        {
            return Attempt.failed("recipe simulation failed: " + exception.getMessage());
        }
        if (output.isEmpty() || !output.getItemHolder().is(expectedOutput))
            return Attempt.failed("recipe produced an unexpected item");

        boolean[] persistentSlots = new boolean[prepared.input().size()];
        boolean statefulRemainder = false;
        for (int i = 0; i < prepared.input().size(); i++)
        {
            ItemStack input = prepared.input().getItem(i);
            ItemStack remainder = remainders.get(i);
            if (input.isEmpty() || remainder.isEmpty()) continue;
            if (ItemStack.isSameItemSameComponents(input, remainder)) persistentSlots[i] = true;
            else if (ItemStack.isSameItem(input, remainder)) statefulRemainder = true;
        }

        long batch = statefulRemainder ? 1 : availableBatch(
                storage, networkSnapshot, reservedAmounts, prepared, persistentSlots, requestedCrafts);
        if (batch < 1) return Attempt.failed("waiting for matching crafting ingredients");

        Map<ItemStackKey, Long> returns = new LinkedHashMap<>();
        if (!escrowOutput) add(returns, output, batch);
        for (int i = 0; i < remainders.size(); i++)
            add(returns, remainders.get(i), persistentSlots[i] ? 1 : batch);
        for (var entry : returns.entrySet())
            if (!storage.insert(entry.getKey(), entry.getValue(), true).isEmpty())
                return Attempt.failed("network has no room for crafting output or remaining items");

        Map<ItemStackKey, Long> consumption = consumption(prepared, persistentSlots, batch);
        List<KeyAmount> extracted = new ArrayList<>();
        LinkedHashMap<ItemStackKey, Long> consumedReserved = new LinkedHashMap<>();
        for (var entry : consumption.entrySet())
        {
            long fromReserved = Math.min(reservedAmounts.getOrDefault(entry.getKey(), 0L), entry.getValue());
            if (fromReserved > 0) consumedReserved.put(entry.getKey(), fromReserved);
            long networkNeeded = entry.getValue() - fromReserved;
            KeyAmount taken = storage.extract(entry.getKey(), networkNeeded, false, false);
            if (taken.amount() != networkNeeded)
            {
                if (!taken.isEmpty()) extracted.add(taken);
                rollbackInputs(storage, extracted);
                return Attempt.failed("crafting ingredients changed before execution");
            }
            extracted.add(taken);
        }

        List<KeyAmount> inserted = new ArrayList<>();
        for (var entry : returns.entrySet())
        {
            KeyAmount remainder = storage.insert(entry.getKey(), entry.getValue(), false);
            long accepted = entry.getValue() - remainder.amount();
            if (accepted > 0) inserted.add(new KeyAmount(entry.getKey(), accepted));
            if (!remainder.isEmpty())
            {
                inserted.forEach(value -> storage.extract(value.key(), value.amount(), false, false));
                rollbackInputs(storage, extracted);
                return Attempt.failed("crafting transaction rolled back because network capacity changed");
            }
        }
        return new Attempt(true, "", SaturatingLongMath.multiply(output.getCount(), batch), batch,
                consumedReserved.entrySet().stream().map(entry ->
                        new RecipePlan.ReservedMaterial(entry.getKey(), entry.getValue())).toList(),
                escrowOutput ? List.of(new RecipePlan.ReservedMaterial(new ItemStackKey(output),
                        SaturatingLongMath.multiply(output.getCount(), batch))) : List.of());
    }

    private static Prepared prepare(UnifiedStorage storage, CraftingRecipe recipe, ServerLevel level,
                                    List<RecipePlan.IngredientSelection> selections,
                                    Map<ItemStackKey, Long> orderReserved,
                                    PlanningSnapshotService.Snapshot networkSnapshot)
    {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<ItemStack> chosen = new ArrayList<>(ingredients.size());
        List<ItemStackKey> slotKeys = new ArrayList<>();
        Map<ItemStackKey, Long> reserved = new LinkedHashMap<>();
        LinkedHashMap<ItemStackKey, Long> combined = new LinkedHashMap<>(orderReserved);
        if (networkSnapshot == null)
        {
            for (KeyAmount available : storage.getStorage())
                if (available.key() instanceof ItemStackKey key && available.amount() > 0)
                    combined.merge(key, available.amount(), SaturatingLongMath::add);
        }
        else for (PlanningSnapshotService.ComponentEntry available : networkSnapshot.componentEntries())
            combined.merge(available.key(), available.amount(), SaturatingLongMath::add);
        List<KeyAmount> availableStacks = combined.entrySet().stream()
                .map(entry -> new KeyAmount(entry.getKey(), entry.getValue())).toList();
        Map<Integer, ResourceLocation> selectedItems = new LinkedHashMap<>();
        for (RecipePlan.IngredientSelection selection : selections)
            selectedItems.put(selection.slot(), selection.item());
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++)
        {
            Ingredient ingredient = ingredients.get(ingredientIndex);
            if (ingredient.isEmpty())
            {
                chosen.add(ItemStack.EMPTY);
                slotKeys.add(ItemStackKey.EMPTY);
                continue;
            }
            ItemStackKey selected = null;
            for (KeyAmount available : availableStacks)
            {
                if (!(available.key() instanceof ItemStackKey key)
                        || available.amount() <= reserved.getOrDefault(key, 0L)) continue;
                ItemStack candidate = key.getReadOnlyStack();
                ResourceLocation selectedItem = selectedItems.get(ingredientIndex);
                if (selectedItem != null && !BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selectedItem))
                    continue;
                if (ingredient.test(candidate)) { selected = key; break; }
            }
            if (selected == null) return null;
            reserved.merge(selected, 1L, Long::sum);
            slotKeys.add(selected);
            chosen.add(selected.getReadOnlyStack().copyWithCount(1));
        }

        CraftingInput input = matchingInput(recipe, chosen, level);
        return input == null ? null : new Prepared(input, List.copyOf(slotKeys));
    }

    static boolean[] reusableIngredientSlots(RecipeHolder<?> holder, Level level,
                                             List<RecipePlan.IngredientSelection> selections)
    {
        List<Ingredient> ingredients = holder.value().getIngredients();
        boolean[] reusable = new boolean[ingredients.size()];
        if (!(holder.value() instanceof CraftingRecipe recipe)) return reusable;
        Map<Integer, ResourceLocation> selectedItems = new LinkedHashMap<>();
        for (RecipePlan.IngredientSelection selection : selections)
            selectedItems.put(selection.slot(), selection.item());
        List<ItemStack> samples = new ArrayList<>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++)
        {
            Ingredient ingredient = ingredients.get(i);
            ItemStack sample = ItemStack.EMPTY;
            ResourceLocation selected = selectedItems.get(i);
            for (ItemStack candidate : ingredient.getItems())
                if (selected == null || BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selected))
                { sample = candidate.copyWithCount(1); break; }
            samples.add(sample);
        }
        try
        {
            CraftingInput input = matchingInput(recipe, samples, level);
            if (input == null) return reusable;
            NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
            for (int i = 0; i < Math.min(ingredients.size(), remaining.size()); i++)
                reusable[i] = !samples.get(i).isEmpty() && !remaining.get(i).isEmpty()
                        && samples.get(i).is(remaining.get(i).getItem());
        }
        catch (RuntimeException ignored) {}
        return reusable;
    }

    private static CraftingInput matchingInput(CraftingRecipe recipe, List<ItemStack> chosen, Level level)
    {
        if (chosen.size() > 9) return null;
        if (recipe instanceof ShapedRecipe shaped)
        {
            CraftingInput input = input(shaped.getWidth(), shaped.getHeight(), chosen);
            if (recipe.matches(input, level)) return input;
        }
        for (int width = 1; width <= 3; width++)
        {
            int height = (chosen.size() + width - 1) / width;
            if (height < 1 || height > 3) continue;
            CraftingInput input = input(width, height, chosen);
            if (recipe.matches(input, level)) return input;
        }
        return null;
    }

    private static CraftingInput input(int width, int height, List<ItemStack> chosen)
    {
        List<ItemStack> slots = new ArrayList<>(chosen);
        while (slots.size() < width * height) slots.add(ItemStack.EMPTY);
        return CraftingInput.of(width, height, slots);
    }

    private static long availableBatch(UnifiedStorage storage, PlanningSnapshotService.Snapshot networkSnapshot,
                                       Map<ItemStackKey, Long> orderReserved,
                                       Prepared prepared, boolean[] persistent,
                                       long requested)
    {
        Map<ItemStackKey, Long> fixed = new LinkedHashMap<>();
        Map<ItemStackKey, Long> perBatch = new LinkedHashMap<>();
        for (int i = 0; i < prepared.slotKeys().size(); i++)
        {
            ItemStackKey key = prepared.slotKeys().get(i);
            if (key == ItemStackKey.EMPTY) continue;
            (persistent[i] ? fixed : perBatch).merge(key, 1L, Long::sum);
        }
        long batch = requested;
        Map<ItemStackKey, Long> snapshotAmounts = new LinkedHashMap<>();
        if (networkSnapshot != null)
            for (PlanningSnapshotService.ComponentEntry value : networkSnapshot.componentEntries())
                snapshotAmounts.put(value.key(), value.amount());
        for (var entry : perBatch.entrySet())
        {
            long networkAmount = networkSnapshot == null ? storage.getStackByKey(entry.getKey()).amount()
                    : snapshotAmounts.getOrDefault(entry.getKey(), 0L);
            long available = SaturatingLongMath.add(networkAmount,
                    orderReserved.getOrDefault(entry.getKey(), 0L))
                    - fixed.getOrDefault(entry.getKey(), 0L);
            if (available < 0) return 0;
            batch = Math.min(batch, available / entry.getValue());
        }
        return batch;
    }

    private static Map<ItemStackKey, Long> consumption(Prepared prepared, boolean[] persistent, long batch)
    {
        Map<ItemStackKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < prepared.slotKeys().size(); i++)
        {
            ItemStackKey key = prepared.slotKeys().get(i);
            if (key == ItemStackKey.EMPTY) continue;
            result.merge(key, persistent[i] ? 1L : batch, SaturatingLongMath::add);
        }
        return result;
    }

    private static void add(Map<ItemStackKey, Long> values, ItemStack stack, long multiplier)
    {
        if (!stack.isEmpty()) values.merge(new ItemStackKey(stack),
                SaturatingLongMath.multiply(stack.getCount(), multiplier),
                SaturatingLongMath::add);
    }

    private static void rollbackInputs(UnifiedStorage storage, List<KeyAmount> extracted)
    {
        extracted.forEach(value -> storage.insert(value.key(), value.amount(), false));
    }

    private record Prepared(CraftingInput input, List<ItemStackKey> slotKeys) {}

    private static Map<ItemStackKey, Long> reservedAmounts(List<RecipePlan.ReservedMaterial> reserved)
    {
        LinkedHashMap<ItemStackKey, Long> result = new LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial material : reserved)
            result.merge(material.key(), material.amount(), SaturatingLongMath::add);
        return result;
    }

    public record Attempt(boolean success, String reason, long output, long crafts,
                          List<RecipePlan.ReservedMaterial> consumedReserved,
                          List<RecipePlan.ReservedMaterial> producedReserved) {
        public Attempt
        {
            consumedReserved = List.copyOf(consumedReserved);
            producedReserved = List.copyOf(producedReserved);
        }
        static Attempt failed(String reason)
        { return new Attempt(false, reason, 0, 0, List.of(), List.of()); }
    }
}
