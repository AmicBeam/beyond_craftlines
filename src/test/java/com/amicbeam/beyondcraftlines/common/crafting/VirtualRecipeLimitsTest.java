package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class VirtualRecipeLimitsTest
{
    @Test void acceptsCompleteNineByNineRecipes()
    { assertDoesNotThrow(() -> VirtualRecipeLimits.requireInputs(81, 81)); }

    @Test void rejectsOversizedRecipesInsteadOfTruncatingThem()
    {
        assertThrows(IllegalArgumentException.class, () -> VirtualRecipeLimits.requireInputs(257, 257));
        assertThrows(IllegalArgumentException.class, () -> VirtualRecipeLimits.requireInputs(81, 8193));
    }
}
