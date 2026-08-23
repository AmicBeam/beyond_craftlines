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
    void substitutesRepeatedEquivalentSlotsAsAGroup()
    {
        var woods = List.of("oak", "birch", "spruce");
        var variants = SingleSubstitutionVariants.from(List.of(woods, List.of("stick"), woods));

        assertEquals(List.of(
                List.of("oak", "stick", "oak"),
                List.of("birch", "stick", "oak"),
                List.of("spruce", "stick", "oak"),
                List.of("oak", "stick", "birch"),
                List.of("oak", "stick", "spruce"),
                List.of("birch", "stick", "birch"),
                List.of("spruce", "stick", "spruce")), variants);
    }

    @Test
    void rejectsAnIngredientWithoutCandidates()
    {
        assertThrows(IllegalArgumentException.class,
                () -> SingleSubstitutionVariants.from(List.of(List.of("iron"), List.of())));
    }

    @Test
    void generationCannotAllocatePastTheServerPlanningBudget()
    {
        PlanningBudget budget = new PlanningBudget(3, Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> SingleSubstitutionVariants.from(
                List.of(List.of("a", "b", "c", "d", "e")), java.util.Objects::equals,
                budget::checkGeneratedVariants));
    }
}
