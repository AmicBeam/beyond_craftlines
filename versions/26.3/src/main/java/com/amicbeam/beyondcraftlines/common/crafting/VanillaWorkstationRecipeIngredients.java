package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;

import java.util.List;

/** Minecraft 26.1 extraction of smithing inputs through the placement-info API. */
final class VanillaWorkstationRecipeIngredients
{
    private VanillaWorkstationRecipeIngredients() {}

    static List<Ingredient> ingredients(Recipe<?> recipe)
    {
        if (!(recipe instanceof SmithingRecipe)) return List.of();
        var placement = recipe.placementInfo();
        return placement == null ? List.of() : List.copyOf(placement.ingredients());
    }
}
