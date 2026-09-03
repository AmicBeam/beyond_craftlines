package com.amicbeam.beyondcraftlines.mixin.emi;

import com.amicbeam.beyondcraftlines.client.integration.emi.EmiClientIntegration;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.WidgetGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reserves a real recipe-button column and adds the Craftlines action to production EMI. */
@Pseudo
@Mixin(targets = "dev.emi.emi.screen.RecipeDisplay", remap = false)
public abstract class RecipeDisplayMixin
{
    @Shadow(remap = false) @Final private int width;
    @Shadow(remap = false) private int rightWidth;
    @Unique private int beyondCraftlines$buttonX = -1;

    @Inject(method = "<init>(Ldev/emi/emi/api/recipe/EmiRecipe;)V", at = @At("TAIL"), remap = false)
    private void beyondCraftlines$reserveButtonSpace(EmiRecipe recipe, CallbackInfo callback)
    {
        if (!EmiClientIntegration.hasRecipeOrderTarget(recipe)) return;
        beyondCraftlines$buttonX = width + (rightWidth == 0 ? 5 : rightWidth + 6);
        rightWidth = beyondCraftlines$buttonX - width + 18;
    }

    @Inject(method = "getWidgets", at = @At("RETURN"), remap = false)
    private void beyondCraftlines$addOrderButton(int x, int y, int availableWidth,
                                                  int availableHeight,
                                                  CallbackInfoReturnable<WidgetGroup> callback)
    {
        WidgetGroup widgets = callback.getReturnValue();
        if (beyondCraftlines$buttonX >= 0 && widgets != null)
            EmiClientIntegration.addRecipeOrderButton(widgets.recipe, widgets,
                    beyondCraftlines$buttonX);
    }
}
