package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.ResourceLocation;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/** Player-selected recipe and ingredient resolutions, validated again by the server planner. */
public final class RecipeResolutionOverrides
{
    public static final RecipeResolutionOverrides EMPTY = new RecipeResolutionOverrides(List.of(), List.of());

    private final Map<String, ResourceLocation> recipes;
    private final Map<IngredientSlot, ResourceLocation> ingredients;

    public RecipeResolutionOverrides(List<RecipeChoice> recipeChoices, List<IngredientChoice> ingredientChoices)
    {
        if (recipeChoices.size() > 16_384 || ingredientChoices.size() > 16_384)
            throw new IllegalArgumentException("too many recipe resolutions");
        SharedResolutionMap<String, ResourceLocation> recipes = new SharedResolutionMap<>();
        for (RecipeChoice choice : recipeChoices)
            recipes.put(choice.output(), choice.recipe(), "duplicate recipe resolution for " + choice.output());
        SharedResolutionMap<IngredientSlot, ResourceLocation> ingredients = new SharedResolutionMap<>();
        for (IngredientChoice choice : ingredientChoices)
            ingredients.put(new IngredientSlot(choice.recipe(), choice.slot()), choice.item(),
                    "duplicate ingredient resolution for " + choice.recipe());
        this.recipes = recipes.copy();
        this.ingredients = ingredients.copy();
    }

    public ResourceLocation recipeFor(IStackKey<?> output)
    { return recipes.get(RecipeResourceResolver.sortKey(output)); }
    public ResourceLocation recipeFor(ResourceLocation output)
    { return recipeFor(new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(output)))); }
    public ResourceLocation ingredientFor(ResourceLocation recipe, int slot)
    { return ingredients.get(new IngredientSlot(recipe, slot)); }

    public boolean completelyResolves(RecipePlan plan)
    {
        for (RecipePlan.Step step : plan.steps())
        {
            if (!step.recipe().equals(recipeFor(step.outputKey()))) return false;
            for (RecipePlan.IngredientSelection selection : step.ingredientSelections())
                if (!selection.item().equals(ingredientFor(step.recipe(), selection.slot()))) return false;
        }
        return true;
    }

    public record RecipeChoice(String output, ResourceLocation recipe)
    {
        public RecipeChoice
        {
            if (output == null || recipe == null) throw new IllegalArgumentException("invalid recipe resolution");
        }
    }

    public record IngredientChoice(ResourceLocation recipe, int slot, ResourceLocation item)
    {
        public IngredientChoice
        {
            if (recipe == null || item == null || slot < 0 || slot > 1024)
                throw new IllegalArgumentException("invalid ingredient resolution");
        }
    }

    private record IngredientSlot(ResourceLocation recipe, int slot) {}
}
