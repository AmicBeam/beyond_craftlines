package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalStockTest
{
    @Test
    void appliesExactKeyDeltasAndRemovesZeroEntries()
    {
        IncrementalStock<String> stock = new IncrementalStock<>();
        stock.replace(Map.of("red", 4L, "blue", 2L));
        long revision = stock.revision();
        stock.apply("red", 3, false);
        stock.apply("blue", 2, false);
        stock.apply("green", 5, true);

        assertEquals(Map.of("red", 1L, "green", 5L), stock.snapshot());
        assertTrue(stock.revision() > revision);
    }
}
