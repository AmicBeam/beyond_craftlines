package com.amicbeam.beyondcraftlines.client.integration.jei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeiRecipeExecutionSourceTest
{
    @Test void declaredNativeMachineUsesOneServerIdentityInBothViewerModes()
    {
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("modern_industrialization:star_altar", true));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("modern_industrialization:star_altar", false));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("mekanism:crushing", false));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("smelting", false));
    }

    @Test
    void keepsNetworkExecutedRecipesOnTheirServerIdentity()
    {
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("crafting"));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("minecraft:smithing"));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("minecraft:stonecutting"));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("smelting"));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("blasting"));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("smoking"));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("mekanism:crushing"));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("minecraft:crafting"));
    }
}
