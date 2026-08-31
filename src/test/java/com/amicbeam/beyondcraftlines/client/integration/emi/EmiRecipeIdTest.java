package com.amicbeam.beyondcraftlines.client.integration.emi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class EmiRecipeIdTest
{
    @Test void unwrapsJemiRecipePaths()
    {
        assertEquals("farmersdelight:cooking/rice_roll_medley_block",
                EmiRecipeId.unwrapPath("/farmersdelight/cooking/rice_roll_medley_block"));
    }

    @Test void rejectsMalformedWrappedIds()
    {
        assertNull(EmiRecipeId.unwrapPath("minecraft/crafting"));
        assertNull(EmiRecipeId.unwrapPath("/minecraft"));
        assertNull(EmiRecipeId.unwrapPath("/minecraft/"));
    }
}
