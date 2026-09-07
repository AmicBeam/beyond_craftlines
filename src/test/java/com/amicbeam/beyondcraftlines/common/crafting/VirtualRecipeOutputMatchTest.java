package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class VirtualRecipeOutputMatchTest
{
    @Test void appliesTheMatcherOnlyToTheExpectedOutputType()
    {
        assertTrue(VirtualRecipeOutputMatch.matches("glass", String.class, "glass"::equals));
        assertFalse(VirtualRecipeOutputMatch.matches("stone", String.class, "glass"::equals));
        assertFalse(VirtualRecipeOutputMatch.matches(1, String.class, value -> true));
        assertFalse(VirtualRecipeOutputMatch.matches(null, String.class, value -> true));
    }

    @Test void neverCallsTheRemappedRecipeFallbackForVirtualOutputs()
    {
        assertTrue(VirtualRecipeOutputMatch.matches(true, "glass", String.class, "glass"::equals,
                () -> fail("virtual recipes must not call their Recipe proxy output method")));
        assertFalse(VirtualRecipeOutputMatch.matches(true, 1, String.class, value -> true,
                () -> fail("an invalid virtual output must still not call the Recipe proxy")));
        assertTrue(VirtualRecipeOutputMatch.matches(false, null, String.class, value -> false, () -> true));
    }
}
