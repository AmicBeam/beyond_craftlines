package com.amicbeam.beyondcraftlines.common.menu;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeIndexVisibilityTest
{
    @Test
    void sharedIndexStillFiltersEachNetworksFamilies()
    {
        Set<String> available = Set.of("example:crusher");
        assertTrue(RecipeIndexVisibility.includes("crafting", available));
        assertTrue(RecipeIndexVisibility.includes("example:crusher", available));
        assertFalse(RecipeIndexVisibility.includes("example:unbound_machine", available));
    }
}
