package com.amicbeam.beyondcraftlines.mixin.emi;

import com.amicbeam.beyondcraftlines.client.integration.emi.EmiClientIntegration;
import com.google.gson.JsonObject;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors EMI's own default-recipe changes into Craftlines client preferences. */
@Pseudo
@Mixin(targets = "dev.emi.emi.bom.BoM", remap = false)
public abstract class BoMPreferenceMixin
{
    @Inject(method = "loadAdded", at = @At("TAIL"), remap = false)
    private static void beyondCraftlines$loadedPreferences(JsonObject data, CallbackInfo callback)
    { EmiClientIntegration.syncAddedPreferencesFromEmi(); }

    @Inject(method = "addRecipe(Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            at = @At("TAIL"), remap = false)
    private static void beyondCraftlines$addedRecipe(EmiRecipe recipe, CallbackInfo callback)
    {
        for (var output : recipe.getOutputs())
            EmiClientIntegration.syncPreferenceFromEmi(output, null);
    }

    @Inject(method = "addRecipe(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            at = @At("TAIL"), remap = false)
    private static void beyondCraftlines$addedRecipeForStack(
            EmiIngredient output, EmiRecipe recipe, CallbackInfo callback)
    { EmiClientIntegration.syncPreferenceFromEmi(output, null); }

    @Inject(method = "removeRecipe(Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            at = @At("TAIL"), remap = false)
    private static void beyondCraftlines$removedRecipe(EmiRecipe recipe, CallbackInfo callback)
    {
        for (var output : recipe.getOutputs())
            EmiClientIntegration.syncPreferenceFromEmi(output, recipe);
    }

    @Inject(method = "removeRecipe(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            at = @At("TAIL"), remap = false)
    private static void beyondCraftlines$removedRecipeForStack(
            EmiIngredient output, EmiRecipe recipe, CallbackInfo callback)
    { EmiClientIntegration.syncPreferenceFromEmi(output, recipe); }
}
