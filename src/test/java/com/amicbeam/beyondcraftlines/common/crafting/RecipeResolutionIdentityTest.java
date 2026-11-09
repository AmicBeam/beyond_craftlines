package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RecipeResolutionIdentityTest
{
    @Test void legacyKeysCannotBecomeExactVariantPreferences()
    {
        String exact = RecipeResourceResolver.identityKey("item", "minecraft", "minecraft:potion", "{potion:fire}");
        assertTrue(RecipeResourceResolver.isExactChoiceKey(exact));
        assertFalse(RecipeResourceResolver.isExactChoiceKey("minecraft:potion"));
        assertFalse(RecipeResourceResolver.isExactChoiceKey("item|minecraft|minecraft:potion"));
        assertFalse(RecipeResourceResolver.isExactChoiceKey(null));
    }

    @Test
    void distinguishesRecipesForDifferentNbtStatesOfTheSameItem()
    {
        String puppet = RecipeResourceResolver.identityKey("stack_type:item", "slashblade",
                "slashblade:slashblade", "{bladeState:\"puppet\"}");
        String scabbard = RecipeResourceResolver.identityKey("stack_type:item", "slashblade",
                "slashblade:slashblade", "{bladeState:\"scabbard\"}");

        assertNotEquals(puppet, scabbard);
        assertEquals(64, puppet.substring(puppet.lastIndexOf('|') + 1).length());
    }
}
