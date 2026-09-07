package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;

import java.util.List;

/** Forge 1.20.1 extraction of smithing inputs omitted by Recipe#getIngredients(). */
final class VanillaWorkstationRecipeIngredients
{
    private VanillaWorkstationRecipeIngredients() {}

    static List<Ingredient> ingredients(Recipe<?> recipe)
    {
        if (!(recipe instanceof SmithingRecipe smithing)) return List.of();
        List<ItemStack> candidates = BuiltInRegistries.ITEM.stream().map(item -> item.getDefaultInstance())
                .filter(stack -> !stack.isEmpty()).toList();
        return SmithingInputMatcher.ordered(candidates, List.of(
                        smithing::isTemplateIngredient,
                        smithing::isBaseIngredient,
                        smithing::isAdditionIngredient))
                .stream().map(values -> Ingredient.of(values.stream())).toList();
    }
}
