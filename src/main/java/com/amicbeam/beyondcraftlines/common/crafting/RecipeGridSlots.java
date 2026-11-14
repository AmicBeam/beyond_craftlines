package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import java.util.ArrayList;
import java.util.List;

/** Associates JEI input views with original grid cells, including cells JEI leaves invisible. */
public final class RecipeGridSlots
{
    private RecipeGridSlots() {}

    public static List<Integer> cells(Object displayed, int inputViews)
    {
        Object recipe = displayed instanceof RecipeHolder<?> holder ? holder.value() : displayed;
        if (!(recipe instanceof ShapedRecipe shaped)) return List.of();
        int size = shaped.getWidth() * shaped.getHeight();
        List<Integer> occupied = new ArrayList<>();
        var ingredients = shaped.getIngredients();
        for (int slot = 0; slot < ingredients.size(); slot++)
            if (!ingredients.get(slot).isEmpty()) occupied.add(slot);
        return cells(size, occupied, inputViews);
    }

    public static List<Integer> cells(int size, List<Integer> occupied, int inputViews)
    {
        if (size < 1 || size > VirtualRecipeLimits.INPUTS) return List.of();
        if (inputViews == size) return java.util.stream.IntStream.range(0, size).boxed().toList();
        return inputViews == occupied.size() ? List.copyOf(occupied) : List.of();
    }
}
