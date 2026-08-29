package com.amicbeam.beyondcraftlines.client.integration.jei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeiRecipeExecutionSourceTest
{
    @Test
    void keepsCraftingRecipesOnTheirServerIdentity()
    {
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("crafting"));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("smelting"));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("minecraft:crafting"));
    }
}
