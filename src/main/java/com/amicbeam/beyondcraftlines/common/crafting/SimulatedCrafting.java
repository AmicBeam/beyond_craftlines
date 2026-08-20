package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe))
            return Attempt.failed("crafting recipe is no longer available: " + recipeId);

        Prepared prepared = prepare(storage, recipe, level);
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

        Map<ItemStackKey, Long> returns = new LinkedHashMap<>();
        add(returns, output);
        remainders.forEach(stack -> add(returns, stack));
        for (var entry : returns.entrySet())
            if (!storage.insert(entry.getKey(), entry.getValue(), true).isEmpty())
                return Attempt.failed("network has no room for crafting output or remaining items");

        List<KeyAmount> extracted = new ArrayList<>();
        for (ItemStackKey key : prepared.consumed())
        {
            KeyAmount taken = storage.extract(key, 1, false, false);
            if (taken.amount() != 1)
            {
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
        return new Attempt(true, "", output.getCount());
    }

    private static Prepared prepare(UnifiedStorage storage, CraftingRecipe recipe, ServerLevel level)
    {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<ItemStack> chosen = new ArrayList<>(ingredients.size());
        List<ItemStackKey> consumed = new ArrayList<>();
        Map<ItemStackKey, Long> reserved = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients)
        {
            if (ingredient.isEmpty())
            {
                chosen.add(ItemStack.EMPTY);
                continue;
            }
            ItemStackKey selected = null;
            for (KeyAmount available : storage.getStorage())
            {
                if (!(available.key() instanceof ItemStackKey key)
                        || available.amount() <= reserved.getOrDefault(key, 0L)) continue;
                ItemStack candidate = key.getReadOnlyStack();
                if (ingredient.test(candidate)) { selected = key; break; }
            }
            if (selected == null) return null;
            reserved.merge(selected, 1L, Long::sum);
            consumed.add(selected);
            chosen.add(selected.getReadOnlyStack().copyWithCount(1));
        }

        CraftingInput input = matchingInput(recipe, chosen, level);
        return input == null ? null : new Prepared(input, List.copyOf(consumed));
    }

    static boolean[] reusableIngredientSlots(RecipeHolder<?> holder, ServerLevel level)
    {
        List<Ingredient> ingredients = holder.value().getIngredients();
        boolean[] reusable = new boolean[ingredients.size()];
        if (!(holder.value() instanceof CraftingRecipe recipe)) return reusable;
        List<ItemStack> samples = ingredients.stream().map(ingredient -> ingredient.isEmpty()
                || ingredient.getItems().length == 0 ? ItemStack.EMPTY : ingredient.getItems()[0].copyWithCount(1)).toList();
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

    private static CraftingInput matchingInput(CraftingRecipe recipe, List<ItemStack> chosen, ServerLevel level)
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

    private static void add(Map<ItemStackKey, Long> values, ItemStack stack)
    {
        if (!stack.isEmpty()) values.merge(new ItemStackKey(stack), (long) stack.getCount(),
                SaturatingLongMath::add);
    }

    private static void rollbackInputs(UnifiedStorage storage, List<KeyAmount> extracted)
    {
        extracted.forEach(value -> storage.insert(value.key(), value.amount(), false));
    }

    private record Prepared(CraftingInput input, List<ItemStackKey> consumed) {}

    public record Attempt(boolean success, String reason, long output) {
        static Attempt failed(String reason) { return new Attempt(false, reason, 0); }
    }
}
