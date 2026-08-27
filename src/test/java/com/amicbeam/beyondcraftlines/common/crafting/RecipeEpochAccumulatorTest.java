package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class RecipeEpochAccumulatorTest
{
    @Test
    void accumulationIsIndependentOfRecipeVisitOrder()
    {
        RecipeEpochAccumulator first = new RecipeEpochAccumulator();
        first.add("crafting", 11);
        first.add("crafting", 29);
        RecipeEpochAccumulator second = new RecipeEpochAccumulator();
        second.add("crafting", 29);
        second.add("crafting", 11);

        assertEquals(first.epoch(Set.of()), second.epoch(Set.of()));
    }

    @Test
    void unavailableFamiliesDoNotAffectNetworkEpoch()
    {
        RecipeEpochAccumulator baseline = new RecipeEpochAccumulator();
        baseline.add("crafting", 11);
        RecipeEpochAccumulator withMachine = new RecipeEpochAccumulator();
        withMachine.add("crafting", 11);
        withMachine.add("example:crusher", 31);

        assertEquals(baseline.epoch(Set.of()), withMachine.epoch(Set.of()));
        assertNotEquals(baseline.epoch(Set.of("example:crusher")),
                withMachine.epoch(Set.of("example:crusher")));
    }

    @Test
    void persistedSnapshotRestoresTheSameEpoch()
    {
        RecipeEpochAccumulator original = new RecipeEpochAccumulator();
        original.add("crafting", 17);
        original.add("example:crusher", 41);
        RecipeEpochAccumulator restored = new RecipeEpochAccumulator();
        restored.restore(original.snapshot());

        assertEquals(original.epoch(Set.of("example:crusher")),
                restored.epoch(Set.of("example:crusher")));
    }
}
