package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class RecipeResolutionIdentityTest
{
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
