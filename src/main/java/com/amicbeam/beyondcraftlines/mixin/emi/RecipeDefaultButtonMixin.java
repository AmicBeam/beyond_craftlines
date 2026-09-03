package com.amicbeam.beyondcraftlines.mixin.emi;

import com.amicbeam.beyondcraftlines.client.integration.emi.EmiClientIntegration;
import dev.emi.emi.api.recipe.EmiRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reconciles Craftlines after EMI's native default-recipe button mutates BoM. */
@Pseudo
@Mixin(targets = "dev.emi.emi.widget.RecipeDefaultButtonWidget", remap = false)
public abstract class RecipeDefaultButtonMixin
{
    @Inject(method = "mouseClicked", at = @At("TAIL"), remap = false)
    private void beyondCraftlines$syncDefault(int mouseX, int mouseY, int button,
                                               CallbackInfoReturnable<Boolean> callback)
    {
        EmiRecipe recipe = ((RecipeButtonAccessor) this).beyondCraftlines$getRecipe();
        for (var output : recipe.getOutputs())
            EmiClientIntegration.syncPreferenceFromEmi(output, recipe);
    }
}
