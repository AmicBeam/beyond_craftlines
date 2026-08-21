package com.amicbeam.beyondcraftlines.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import java.util.List;

/** The selected direct inputs and output for a recipe-tree node. */
public record RecipePreviewTooltip(List<KeyAmount> inputs, KeyAmount output) implements TooltipComponent
{
    public RecipePreviewTooltip
    {
        inputs = List.copyOf(inputs);
    }
}
