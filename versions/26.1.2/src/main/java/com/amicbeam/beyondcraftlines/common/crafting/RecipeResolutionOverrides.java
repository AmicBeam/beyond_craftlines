package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.Identifier;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Player-selected recipe and ingredient resolutions, validated again by the server planner. */
public final class RecipeResolutionOverrides
{
    public static final RecipeResolutionOverrides EMPTY = new RecipeResolutionOverrides(List.of(), List.of());

    private final Map<String, Identifier> recipes;
    private final Map<IngredientSlot, String> ingredients;

    public RecipeResolutionOverrides(List<RecipeChoice> recipeChoices, List<IngredientChoice> ingredientChoices)
    {
        if (recipeChoices.size() > 16_384 || ingredientChoices.size() > 16_384)
            throw new IllegalArgumentException("too many recipe resolutions");
        SharedResolutionMap<String, Identifier> recipes = new SharedResolutionMap<>();
        for (RecipeChoice choice : recipeChoices)
            recipes.put(choice.output(), choice.recipe(), "duplicate recipe resolution for " + choice.output());
        SharedResolutionMap<IngredientSlot, String> ingredients = new SharedResolutionMap<>();
        for (IngredientChoice choice : ingredientChoices)
            ingredients.put(new IngredientSlot(choice.recipe(), choice.slot()), choice.selection(),
                    "duplicate ingredient resolution for " + choice.recipe());
        this.recipes = recipes.copy();
        this.ingredients = ingredients.copy();
    }

    public Identifier recipeFor(IStackKey<?> output)
    {
        Identifier exact = recipes.get(RecipeResourceResolver.resolutionKey(output));
        return exact == null ? recipes.get(RecipeResourceResolver.sortKey(output)) : exact;
    }
    public Identifier recipeFor(Identifier output)
    { return recipeFor(new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(output)))); }
    public String ingredientFor(Identifier recipe, int slot)
    { return ingredients.get(new IngredientSlot(recipe, slot)); }
    public Set<Identifier> selectedRecipes() { return Set.copyOf(recipes.values()); }
    public List<RecipeChoice> recipeChoices()
    { return recipes.entrySet().stream().map(entry -> new RecipeChoice(entry.getKey(), entry.getValue())).toList(); }
    public List<IngredientChoice> ingredientChoices()
    { return ingredients.entrySet().stream().map(entry -> new IngredientChoice(
            entry.getKey().recipe(), entry.getKey().slot(), entry.getValue())).toList(); }

    public boolean completelyResolves(RecipePlan plan)
    {
        for (RecipePlan.Step step : plan.steps())
        {
            if (!step.recipe().equals(recipeFor(step.outputKey()))) return false;
            for (RecipePlan.IngredientSelection selection : step.ingredientSelections())
                if (!selection.selection().equals(ingredientFor(step.recipe(), selection.slot()))) return false;
        }
        return true;
    }

    public record RecipeChoice(String output, Identifier recipe)
    {
        public RecipeChoice
        {
            if (output == null || recipe == null) throw new IllegalArgumentException("invalid recipe resolution");
        }
    }

    public record IngredientChoice(Identifier recipe, int slot, String selection)
    {
        public IngredientChoice(Identifier recipe, int slot, Identifier item)
        { this(recipe, slot, IngredientSelectionKey.legacy(item)); }
        public IngredientChoice
        {
            if (recipe == null || selection == null || selection.isBlank()
                    || selection.length() > 512 || slot < 0 || slot > 1024)
                throw new IllegalArgumentException("invalid ingredient resolution");
        }
        public Identifier item() { return FluidContainerChoice.itemOrNull(selection); }
    }

    private record IngredientSlot(Identifier recipe, int slot) {}
}
