package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

final class PlanningCatalogCacheCountsTest
{
    @Test void catalogCountsHaveNoRecipeEntryOrTextQuota() throws Exception
    {
        var counts = new PlanningCatalogCacheCounts();
        counts.addRecipes(2_000_000);
        counts.addSlots(1_000_000_000);
        counts.addCandidates(1_000_000_000);
        counts.addByproducts(1_000_000_000);
        counts.addStringBytes(1_000_000_000);
        counts.addStringBytes(1_000_000_000);
        counts.addStringBytes(1_000_000_000);
        assertEquals(3_002_000_000L, counts.entries());
        assertEquals(3_000_000_000L, counts.stringBytes());
    }

    @Test void rejectsNegativeLengthsFromCorruptFiles() throws Exception
    {
        var counts = new PlanningCatalogCacheCounts();
        assertThrows(IOException.class, () -> counts.addRecipes(-1));
        assertThrows(IOException.class, () -> counts.addSlots(-1));
        assertThrows(IOException.class, () -> counts.addCandidates(-1));
        assertThrows(IOException.class, () -> counts.addByproducts(-1));
        assertThrows(IOException.class, () -> counts.addStringBytes(-1));
        assertEquals(0, counts.entries());
        assertEquals(0, counts.stringBytes());
    }
}
