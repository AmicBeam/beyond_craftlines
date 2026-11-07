package com.amicbeam.beyondcraftlines.common.menu;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeIndexVisibilityTest
{
    @Test
    void boundFurnacesKeepTheirNativeRecipesInThePlanningCatalog()
    {
        for (String family : Set.of("smelting", "blasting", "smoking"))
        {
            assertTrue(RecipeIndexVisibility.includesPlanningRecipe(family, false, Set.of(family)));
            assertFalse(RecipeIndexVisibility.includesPlanningRecipe(family, false, Set.of()));
        }
    }

    @Test
    void sharedIndexStillFiltersEachNetworksFamilies()
    {
        Set<String> available = Set.of("example:crusher", "minecraft:smithing", "minecraft:stonecutting");
        assertTrue(RecipeIndexVisibility.includes("crafting", available));
        assertTrue(RecipeIndexVisibility.includes("minecraft:smithing", available));
        assertTrue(RecipeIndexVisibility.includes("minecraft:stonecutting", available));
        assertTrue(RecipeIndexVisibility.includes("example:crusher", available));
        assertFalse(RecipeIndexVisibility.includes("example:unbound_machine", available));
    }

    @Test
    void planningCatalogKeepsBoundJeiVirtualRecipesWithoutAdmittingNativeMachineRecipes()
    {
        Set<String> available = Set.of("mekanism:crushing");

        assertTrue(RecipeIndexVisibility.includesPlanningRecipe(
                "mekanism:crushing", true, available));
        assertFalse(RecipeIndexVisibility.includesPlanningRecipe(
                "mekanism:crushing", false, available));
        assertFalse(RecipeIndexVisibility.includesPlanningRecipe(
                "create:mixing", true, available));
        assertTrue(RecipeIndexVisibility.includesPlanningRecipe(
                "crafting", false, available));
    }
    @Test void blacklistPreservesMachineExclusiveAndOriginalCraftingRoutes()
    {
        Set<String> families = Set.of("create:automatic_shaped", "create:mechanical_crafting", "create:mixing", "create:fan_blasting");
        assertFalse(RecipeIndexVisibility.includesPlanningRecipe("create:automatic_shaped", true, families));
        assertTrue(RecipeIndexVisibility.includesPlanningRecipe("create:mechanical_crafting", true, families));
        assertTrue(RecipeIndexVisibility.includesPlanningRecipe("create:mixing", true, families));
        assertTrue(RecipeIndexVisibility.includesPlanningRecipe("create:fan_blasting", true, families));
        assertTrue(RecipeIndexVisibility.includesPlanningRecipe("crafting", false, families));
    }
}
