package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class LinkedSlotVariantsTest
{
    @Test void preservesInputOutputPairsAndIndependentAlternatives()
    {
        var variants = LinkedSlotVariants.expand(List.of(List.of("oak", "birch"),
                List.of("oak_stairs", "birch_stairs"), List.of("hammer", "mallet")),
                List.of(List.of(0, 1)), 16);
        assertEquals(List.of(List.of(List.of("oak"), List.of("oak_stairs"), List.of("hammer", "mallet")),
                List.of(List.of("birch"), List.of("birch_stairs"), List.of("hammer", "mallet"))), variants);
    }

    @Test void overlappingLinksRemainOneConsistentChoice()
    {
        var variants = LinkedSlotVariants.expand(List.of(List.of("a", "b"), List.of("c", "d"),
                List.of("e", "f")), List.of(List.of(0, 1), List.of(1, 2)), 2);
        assertEquals(2, variants.size());
        assertEquals(List.of(List.of("b"), List.of("d"), List.of("f")), variants.get(1));
    }

    @Test void rejectsIncompleteAndOversizedVariantSets()
    {
        assertThrows(IllegalArgumentException.class, () -> LinkedSlotVariants.expand(
                List.of(List.of("a", "b"), List.of("c")), List.of(List.of(0, 1)), 16));
        assertThrows(IllegalArgumentException.class, () -> LinkedSlotVariants.expand(
                List.of(List.of("a", "b"), List.of("c", "d")), List.of(List.of(0, 1)), 1));
    }
}
