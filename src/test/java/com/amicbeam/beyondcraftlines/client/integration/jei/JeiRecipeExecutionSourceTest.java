package com.amicbeam.beyondcraftlines.client.integration.jei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeiRecipeExecutionSourceTest
{
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
