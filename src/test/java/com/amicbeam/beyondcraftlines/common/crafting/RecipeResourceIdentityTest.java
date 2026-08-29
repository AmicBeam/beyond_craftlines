package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class RecipeResourceIdentityTest
{
    @Test
    void distinguishesDetailedComponentsOnTheSameItem()
    {
        String awakened = RecipeResourceResolver.identityKey("stack_type:item", "minecraft",
                "slashblade:slashblade", "{item:\"slashblade:slashblade\",tag:{bladeState:\"awakened\"}}");
        String broken = RecipeResourceResolver.identityKey("stack_type:item", "minecraft",
                "slashblade:slashblade", "{item:\"slashblade:slashblade\",tag:{bladeState:\"broken\"}}");

        assertNotEquals(awakened, broken);
    }

    @Test
    void producesStableCompactKeysForTheSameSerializedResource()
    {
        String first = RecipeResourceResolver.identityKey("stack_type:item", "minecraft",
                "slashblade:slashblade", "{tag:{killCount:1234,refine:12}}");
        String second = RecipeResourceResolver.identityKey("stack_type:item", "minecraft",
                "slashblade:slashblade", "{tag:{killCount:1234,refine:12}}");

        assertEquals(first, second);
        assertEquals(64, first.substring(first.lastIndexOf('|') + 1).length());
    }
}
