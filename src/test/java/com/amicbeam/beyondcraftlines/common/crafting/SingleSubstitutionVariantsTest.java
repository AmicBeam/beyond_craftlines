package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SingleSubstitutionVariantsTest
{
    @Test
    void producesBaselineAndEverySingleSlotAlternative()
    {
        var variants = SingleSubstitutionVariants.from(List.of(
                List.of("iron", "copper", "gold"), List.of("stick", "rod")));

        assertEquals(List.of(
                List.of("iron", "stick"),
                List.of("copper", "stick"),
                List.of("gold", "stick"),
                List.of("iron", "rod")), variants);
    }

    @Test
    void avoidsCartesianProductGrowth()
    {
        var variants = SingleSubstitutionVariants.from(List.of(
                List.of(1, 2, 3), List.of(4, 5, 6), List.of(7, 8, 9)));

        assertEquals(7, variants.size());
    }

    @Test
    void rejectsAnIngredientWithoutCandidates()
    {
        assertThrows(IllegalArgumentException.class,
                () -> SingleSubstitutionVariants.from(List.of(List.of("iron"), List.of())));
    }
}
