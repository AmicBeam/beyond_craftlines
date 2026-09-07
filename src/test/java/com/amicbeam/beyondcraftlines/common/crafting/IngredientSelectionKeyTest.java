package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class IngredientSelectionKeyTest
{
    @Test void exactPotionSelectionRetainsTheComponentAwareResolutionIdentity()
    {
        String water = RecipeResourceResolver.identityKey("stack_type:item", "minecraft",
                "minecraft:potion", "{potion:\"minecraft:water\"}");
        String awkward = RecipeResourceResolver.identityKey("stack_type:item", "minecraft",
                "minecraft:potion", "{potion:\"minecraft:awkward\"}");

        assertNotEquals(water, awkward);
        assertNotEquals(IngredientSelectionKey.exactResolution(water),
                IngredientSelectionKey.exactResolution(awkward));
        assertTrue(IngredientSelectionKey.exactResolution(water).startsWith("exact:"));
    }
}
