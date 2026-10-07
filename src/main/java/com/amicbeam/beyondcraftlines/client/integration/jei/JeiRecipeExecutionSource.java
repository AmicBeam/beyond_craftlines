package com.amicbeam.beyondcraftlines.client.integration.jei;

/** Chooses recipes that must retain their authoritative server identity for execution. */
final class JeiRecipeExecutionSource
{
    private JeiRecipeExecutionSource() {}

    static boolean usesServerRecipe(String family)
    { return usesServerRecipe(family, false); }

    static boolean usesServerRecipe(String family, boolean nativeFallbackAllowed)
    { return nativeFallbackAllowed || com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes
            .isPotentialNetworkExecutable(family); }

    static boolean usesServerRecipe(Object displayedRecipe)
    {
        net.minecraft.world.item.crafting.Recipe<?> recipe = displayedRecipe instanceof
                net.minecraft.world.item.crafting.RecipeHolder<?> holder ? holder.value()
                : displayedRecipe instanceof net.minecraft.world.item.crafting.Recipe<?> value ? value : null;
        return recipe != null && usesServerRecipe(com.amicbeam.beyondcraftlines.common.crafting
                .RecipePlanningService.family(recipe.getType()),
                com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry.allowsNativeFallback(recipe));
    }
}
