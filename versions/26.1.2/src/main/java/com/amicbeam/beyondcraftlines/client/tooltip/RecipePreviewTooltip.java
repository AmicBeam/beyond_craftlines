package com.amicbeam.beyondcraftlines.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import java.util.List;

/** Inputs and output, with optional shaped-recipe cells indexing the unmerged inputs. */
public record RecipePreviewTooltip(List<KeyAmount> inputs, KeyAmount output,
                                   int gridWidth, int gridHeight, List<Integer> cells) implements TooltipComponent
{
    public RecipePreviewTooltip
    {
        inputs = List.copyOf(inputs);
        cells = List.copyOf(cells);
    }

    public RecipePreviewTooltip(List<KeyAmount> inputs, KeyAmount output)
    { this(inputs, output, 0, 0, List.of()); }

    public RecipePreviewTooltip withShape(Recipe<?> recipe, List<Integer> inputSlots)
    {
        if (!(recipe instanceof ShapedRecipe shaped)) return this;
        int width = shaped.getWidth(), height = shaped.getHeight();
        if (width <= 0 || height <= 0 || inputSlots.size() != inputs.size()) return this;
        List<Integer> placement = shaped.placementInfo().slotsToIngredientIndex();
        if (placement.size() != width * height) return this;
        List<Integer> mapped = placement.stream().map(slot -> slot < 0 ? -1 : inputSlots.indexOf(slot)).toList();
        return new RecipePreviewTooltip(inputs, output, width, height, mapped);
    }
}
