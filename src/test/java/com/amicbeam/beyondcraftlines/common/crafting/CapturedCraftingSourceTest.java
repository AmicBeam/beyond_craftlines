package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class CapturedCraftingSourceTest
{
    @Test void sourceAndGridSurviveTransportAndRemainDistinctBetweenVariants()
    {
        var diagonal = new CapturedCraftingSource("test:dynamic", 3, 3, List.of(0, 4, 8));
        assertEquals(diagonal, CapturedCraftingSource.decode(diagonal.encode()));
        assertNotEquals(diagonal, new CapturedCraftingSource("test:dynamic", 3, 3, List.of(0, 1, 2)));
        assertNotEquals(diagonal, new CapturedCraftingSource("test:other", 3, 3, List.of(0, 4, 8)));
        assertNull(CapturedCraftingSource.decode(""));
    }

    @Test void rejectsDuplicateOutOfGridAndUnboundedPositions()
    {
        assertThrows(IllegalArgumentException.class, () -> new CapturedCraftingSource("test:r", 2, 1, List.of(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new CapturedCraftingSource("test:r", 2, 1, List.of(2)));
        assertThrows(IllegalArgumentException.class, () -> new CapturedCraftingSource("test:r", 4, 1, List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> CapturedCraftingSource.decode("test:r|3|3|0,1|extra"));
    }

    @Test void twoIdenticalSlabsStayPairedWithTheirOwnBlock()
    {
        var variants = LinkedSlotVariants.expand(List.of(List.of("stone_slab", "oak_slab"),
                List.of("stone_slab", "oak_slab"), List.of("stone", "oak_planks")), List.of(List.of(0, 1, 2)), 256);
        assertEquals(List.of(List.of(List.of("stone_slab"), List.of("stone_slab"), List.of("stone")),
                List.of(List.of("oak_slab"), List.of("oak_slab"), List.of("oak_planks"))), variants);
    }
}
