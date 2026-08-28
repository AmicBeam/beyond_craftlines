package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SelfIncrementRecipeTest
{
    @Test
    void smithingTemplateUsesOneSeedAndTenGrowthCrafts()
    {
        var shape = SelfIncrementRecipe.analyze(2, 1, 1, 10);

        assertTrue(shape.selfIncrement());
        assertEquals(1, shape.seed());
        assertEquals(1, shape.netOutputPerCraft());
        assertEquals(10, shape.crafts());
    }

    @Test
    void ordinaryRecipeKeepsGrossOutputBatching()
    {
        var shape = SelfIncrementRecipe.analyze(4, 0, 0, 10);

        assertFalse(shape.selfIncrement());
        assertEquals(3, shape.crafts());
    }

    @Test
    void nonGrowingFeedbackRemainsAnOrdinaryCycle()
    {
        var shape = SelfIncrementRecipe.analyze(1, 1, 1, 10);

        assertFalse(shape.selfIncrement());
        assertEquals(10, shape.crafts());
    }
}
