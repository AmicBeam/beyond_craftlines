package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeGridSlotsTest
{
    @Test void jeiCanExposeEmptyGridSlotsOrOmitThemWithoutCollapsingPositions()
    {
        List<Integer> occupied = List.of(1, 6, 8);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), RecipeGridSlots.cells(9, occupied, 9));
        assertEquals(occupied, RecipeGridSlots.cells(9, occupied, 3));
        assertTrue(RecipeGridSlots.cells(9, occupied, 4).isEmpty());
    }
}
