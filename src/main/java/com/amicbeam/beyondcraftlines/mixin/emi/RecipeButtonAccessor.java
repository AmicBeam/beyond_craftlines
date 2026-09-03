package com.amicbeam.beyondcraftlines.mixin.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "dev.emi.emi.widget.RecipeButtonWidget", remap = false)
public interface RecipeButtonAccessor
{
    @Accessor(value = "recipe", remap = false)
    EmiRecipe beyondCraftlines$getRecipe();
}
