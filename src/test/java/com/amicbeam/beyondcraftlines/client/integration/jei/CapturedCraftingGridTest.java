package com.amicbeam.beyondcraftlines.client.integration.jei;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class CapturedCraftingGridTest
{
    @Test void preservesEmptyCellsAndOriginalInputSlotOrder()
    {
        var source = JeiVirtualRecipeLayouts.craftingGrid("test:dynamic", List.of(new int[]{54, 54}, new int[]{18, 18}));
        assertEquals(3, source.width());
        assertEquals(3, source.height());
        assertEquals(List.of(8, 0), source.slots());
    }

    @Test void rejectsAmbiguousLayoutInsteadOfGuessingAnInputPattern()
    {
        assertThrows(IllegalArgumentException.class, () -> JeiVirtualRecipeLayouts.craftingGrid("test:dynamic",
                List.of(new int[]{0, 0}, new int[]{17, 0})));
        assertThrows(IllegalArgumentException.class, () -> JeiVirtualRecipeLayouts.craftingGrid("test:dynamic",
                List.of(new int[]{0, 0}, new int[]{54, 0})));
    }
}
