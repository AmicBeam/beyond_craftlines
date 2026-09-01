package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VanillaRecipeBatchingTest
{
    @Test
    void brewingProducesThreePotionsPerDisplayedRecipe()
    {
        long output = VanillaRecipeBatching.outputAmount("minecraft:brewing", 1);
        assertEquals(3, output);
        assertEquals(1, SelfIncrementRecipe.analyze(output, 0, 0, 1).crafts());
        assertEquals(3, 3 * SelfIncrementRecipe.analyze(output, 0, 0, 1).crafts(),
                "one brewing batch consumes three potion inputs, not nine");
        assertTrue(VanillaRecipeBatching.validUploadedOutputAmount("minecraft:brewing", 3));
        assertFalse(VanillaRecipeBatching.validUploadedOutputAmount("minecraft:brewing", 1));
    }

    @Test
    void otherVirtualRecipesKeepTheirDisplayedOutputAmount()
    { assertEquals(2, VanillaRecipeBatching.outputAmount("example:machine", 2)); }
}
