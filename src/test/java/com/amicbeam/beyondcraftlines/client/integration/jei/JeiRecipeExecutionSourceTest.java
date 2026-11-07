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
    @Test void presentationCategoryDoesNotInheritTheUnderlyingWorkstationIdentity()
    {
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("create:fan_blasting", "smelting", false, false));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("create:fan_blasting", "blasting", false, false));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("create:fan_smoking", "smoking", false, false));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("test:auto_crafting", "crafting", false, false));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("minecraft:furnace", "smelting", false, false));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("minecraft:crafting", "crafting", false, false));
        assertFalse(JeiRecipeExecutionSource.usesServerRecipe("minecraft:crafting", "crafting", false, true));
        assertTrue(JeiRecipeExecutionSource.usesServerRecipe("test:custom", "test:native", true, false));
    }
}
