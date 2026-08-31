package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage.encode;

/** Executes one real crafting-table operation against network stacks, including dynamic output and remainders. */
public final class SimulatedCrafting
{
    private SimulatedCrafting() {}

    /** Deterministic container/remaining items for one crafting operation; reusable tools are excluded. */
    public static List<ItemStack> previewRemainders(RecipeHolder<?> holder, Level level,
                                                    List<RecipePlan.IngredientSelection> selections)
    { return previewRemainders(holder, level, selections, List.of()); }

    /** Remainders visible to the network; virtual bucket containers are deliberately suppressed. */
    public static List<ItemStack> previewRemainders(RecipeHolder<?> holder, Level level,
                                                    List<RecipePlan.IngredientSelection> selections,
                                                    List<RecipePlan.Material> plannedInputs)
    {
        if (!(holder.value() instanceof CraftingRecipe recipe)) return List.of();
        List<ItemStack> samples = selectedSamples(holder, selections);
        try
        {
            CraftingInput input = matchingInput(recipe, samples, level);
            if (input == null) return List.of();
            NonNullList<ItemStack> values = recipe.getRemainingItems(input);
            List<ItemStack> result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++)
            {
                if (isPlannedFluidProxy(i, plannedInputs)) continue;
                ItemStack remainder = values.get(i);
                ItemStack source = input.getItem(i);
                if (remainder.isEmpty() || ItemStack.isSameItem(source, remainder)) continue;
                result.add(remainder.copy());
            }
            return List.copyOf(result);
        }
        catch (RuntimeException ignored) { return List.of(); }
    }

    /**
     * Finds crafting slots whose selected item is a Beyond Dimensions-compatible fluid container and whose
     * real recipe remainder is an empty bucket. The returned amount is the capability-reported fluid amount,
     * so milk and modded buckets are handled without item or volume allowlists.
     */
    public static Map<Integer, KeyAmount> bucketFluidInputs(RecipeHolder<?> holder, Level level,
                                                            List<RecipePlan.IngredientSelection> selections)
    {
        if (!(holder.value() instanceof CraftingRecipe recipe)) return Map.of();
        List<ItemStack> samples = selectedSamples(holder, selections);
        try
        {
            CraftingInput input = matchingInput(recipe, samples, level);
            if (input == null) return Map.of();
            NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);
            LinkedHashMap<Integer, KeyAmount> result = new LinkedHashMap<>();
            for (int slot = 0; slot < Math.min(input.size(), remainders.size()); slot++)
            {
                ItemStack source = input.getItem(slot);
                KeyAmount proxy = fluidProxy(source, remainders.get(slot));
                if (proxy != null) result.put(slot, proxy);
            }
            return Map.copyOf(result);
        }
        catch (LinkageError | RuntimeException ignored) { return Map.of(); }
    }

    static KeyAmount fluidProxy(ItemStack source, ItemStack remainder)
    {
        if (source.isEmpty() || !remainder.is(Items.BUCKET)) return null;
        try
        {
            var fluid = FluidUtil.getFluidContained(source).orElse(null);
            return fluid == null || fluid.isEmpty() || fluid.getAmount() < 1 ? null
                    : new KeyAmount(new FluidStackKey(fluid), fluid.getAmount());
        }
        catch (LinkageError | RuntimeException ignored) { return null; }
    }

    private static List<ItemStack> selectedSamples(RecipeHolder<?> holder,
                                                   List<RecipePlan.IngredientSelection> selections)
    {
        Map<Integer, ResourceLocation> selectedItems = new LinkedHashMap<>();
        for (RecipePlan.IngredientSelection selection : selections)
            selectedItems.put(selection.slot(), selection.item());
        List<ItemStack> samples = new ArrayList<>();
        List<Ingredient> ingredients = RecipeIngredientResolver.ingredients(holder.value());
        for (int i = 0; i < ingredients.size(); i++)
        {
            ItemStack sample = ItemStack.EMPTY;
            ResourceLocation selected = selectedItems.get(i);
            for (ItemStack candidate : ingredients.get(i).getItems())
                if (selected == null || BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selected))
                { sample = candidate.copyWithCount(Math.max(1, candidate.getCount())); break; }
            samples.add(sample);
        }
        return samples;
    }

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
    { return craftBatch(level, storage, recipeId, expectedOutput, requestedCrafts, selections,
            orderReserved, escrowOutput, networkSnapshot, List.of()); }

    public static Attempt craftBatch(ServerLevel level, UnifiedStorage storage, ResourceLocation recipeId,
                                     ResourceLocation expectedOutput, long requestedCrafts,
                                     List<RecipePlan.IngredientSelection> selections,
                                     List<RecipePlan.ReservedMaterial> orderReserved,
                                     boolean escrowOutput, PlanningSnapshotService.Snapshot networkSnapshot,
                                     List<RecipePlan.Material> plannedInputs)
    {
        if (requestedCrafts < 1) return Attempt.failed(encode("crafting_invalid_batch"));
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe))
            return Attempt.failed(encode("crafting_recipe_unavailable", recipeId));

        Map<IStackKey<?>, Long> reservedAmounts = reservedAmounts(orderReserved);
        Map<Integer, KeyAmount> discoveredProxies = bucketFluidInputs(holder, level, selections);
        LinkedHashMap<Integer, KeyAmount> activeProxies = new LinkedHashMap<>();
        for (RecipePlan.Material input : plannedInputs)
        {
            KeyAmount proxy = discoveredProxies.get(input.ingredientSlot());
            if (proxy != null && input.key() instanceof FluidStackKey
                    && input.key().isSame(proxy.key())) activeProxies.put(input.ingredientSlot(), proxy);
        }
        Prepared prepared = prepare(storage, recipe, level, selections, reservedAmounts,
                networkSnapshot, activeProxies);
        if (prepared == null) return Attempt.failed(encode("crafting_waiting_inputs"));

        ItemStack output;
        NonNullList<ItemStack> remainders;
        try
        {
            output = recipe.assemble(prepared.input(), level.registryAccess());
            remainders = recipe.getRemainingItems(prepared.input());
        }
        catch (RuntimeException exception)
        {
            return Attempt.failed(encode("crafting_simulation_failed"));
        }
        if (output.isEmpty() || !output.getItemHolder().is(expectedOutput))
            return Attempt.failed(encode("crafting_unexpected_output"));

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
        if (batch < 1) return Attempt.failed(encode("crafting_waiting_inputs"));

        Map<ItemStackKey, Long> returns = new LinkedHashMap<>();
        if (!escrowOutput) add(returns, output, batch);
        for (int i = 0; i < remainders.size(); i++)
            if (!prepared.proxySlots().contains(i))
                add(returns, remainders.get(i), persistentSlots[i] ? 1 : batch);
        for (var entry : returns.entrySet())
            if (!storage.insert(entry.getKey(), entry.getValue(), true).isEmpty())
                return Attempt.failed(encode("crafting_network_full"));

        Map<IStackKey<?>, Long> consumption = consumption(prepared, persistentSlots, batch);
        List<KeyAmount> extracted = new ArrayList<>();
        LinkedHashMap<IStackKey<?>, Long> consumedReserved = new LinkedHashMap<>();
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
                return Attempt.failed(encode("crafting_inputs_changed"));
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
                return Attempt.failed(encode("crafting_rolled_back"));
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
                                    Map<IStackKey<?>, Long> orderReserved,
                                    PlanningSnapshotService.Snapshot networkSnapshot,
                                    Map<Integer, KeyAmount> fluidProxies)
    {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<ItemStack> chosen = new ArrayList<>(ingredients.size());
        List<IStackKey<?>> slotKeys = new ArrayList<>();
        List<Long> slotAmounts = new ArrayList<>();
        Map<IStackKey<?>, Long> reserved = new LinkedHashMap<>();
        LinkedHashMap<IStackKey<?>, Long> combined = new LinkedHashMap<>(orderReserved);
        if (networkSnapshot == null)
        {
            for (KeyAmount available : storage.getStorage())
                if (available.amount() > 0)
                    combined.merge(available.key(), available.amount(), SaturatingLongMath::add);
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
                slotAmounts.add(0L);
                continue;
            }
            KeyAmount fluidProxy = fluidProxies.get(ingredientIndex);
            if (fluidProxy != null)
            {
                long alreadyReserved = reserved.getOrDefault(fluidProxy.key(), 0L);
                if (combined.getOrDefault(fluidProxy.key(), 0L) - alreadyReserved < fluidProxy.amount())
                    return null;
                ItemStack sample = selectedSample(ingredient, selectedItems.get(ingredientIndex));
                if (sample.isEmpty()) return null;
                reserved.merge(fluidProxy.key(), fluidProxy.amount(), SaturatingLongMath::add);
                slotKeys.add(fluidProxy.key());
                slotAmounts.add(fluidProxy.amount());
                chosen.add(sample);
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
            slotAmounts.add(1L);
            chosen.add(selected.getReadOnlyStack().copyWithCount(1));
        }

        CraftingInput input = matchingInput(recipe, chosen, level);
        return input == null ? null : new Prepared(input, List.copyOf(slotKeys),
                List.copyOf(slotAmounts), Set.copyOf(fluidProxies.keySet()));
    }

    private static ItemStack selectedSample(Ingredient ingredient, ResourceLocation selected)
    {
        for (ItemStack candidate : ingredient.getItems())
            if (selected == null || BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selected))
                return candidate.copyWithCount(1);
        return ItemStack.EMPTY;
    }

    public static boolean[] reusableIngredientSlots(RecipeHolder<?> holder, Level level,
                                                    List<RecipePlan.IngredientSelection> selections)
    {
        var virtual = VirtualProvisionerRecipeRegistry.descriptor(holder.value());
        if (virtual != null)
        {
            boolean[] reusable = new boolean[virtual.inputs().size()];
            for (int i = 0; i < reusable.length; i++) reusable[i] = virtual.inputs().get(i).use().sharedReusable();
            return reusable;
        }
        List<Ingredient> ingredients = RecipeIngredientResolver.ingredients(holder.value());
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
                                       Map<IStackKey<?>, Long> orderReserved,
                                       Prepared prepared, boolean[] persistent,
                                       long requested)
    {
        Map<IStackKey<?>, Long> fixed = new LinkedHashMap<>();
        Map<IStackKey<?>, Long> perBatch = new LinkedHashMap<>();
        for (int i = 0; i < prepared.slotKeys().size(); i++)
        {
            IStackKey<?> key = prepared.slotKeys().get(i);
            if (key == ItemStackKey.EMPTY) continue;
            (persistent[i] ? fixed : perBatch).merge(key, prepared.slotAmounts().get(i),
                    SaturatingLongMath::add);
        }
        long batch = requested;
        Map<IStackKey<?>, Long> snapshotAmounts = new LinkedHashMap<>();
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

    private static Map<IStackKey<?>, Long> consumption(Prepared prepared, boolean[] persistent, long batch)
    {
        Map<IStackKey<?>, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < prepared.slotKeys().size(); i++)
        {
            IStackKey<?> key = prepared.slotKeys().get(i);
            if (key == ItemStackKey.EMPTY) continue;
            long amount = prepared.slotAmounts().get(i);
            result.merge(key, persistent[i] ? amount : SaturatingLongMath.multiply(amount, batch),
                    SaturatingLongMath::add);
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

    private record Prepared(CraftingInput input, List<IStackKey<?>> slotKeys, List<Long> slotAmounts,
                            Set<Integer> proxySlots) {}

    private static Map<IStackKey<?>, Long> reservedAmounts(List<RecipePlan.ReservedMaterial> reserved)
    {
        LinkedHashMap<IStackKey<?>, Long> result = new LinkedHashMap<>();
        for (RecipePlan.ReservedMaterial material : reserved)
            result.merge(material.key(), material.amount(), SaturatingLongMath::add);
        return result;
    }

    private static boolean isPlannedFluidProxy(int slot, List<RecipePlan.Material> inputs)
    {
        return inputs.stream().anyMatch(input -> input.ingredientSlot() == slot
                && input.key() instanceof FluidStackKey);
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
