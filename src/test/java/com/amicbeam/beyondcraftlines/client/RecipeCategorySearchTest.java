package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeCategorySearchTest
{
    @Test void acceptsIdFragmentsLocalizedNamesAndBlankQueries()
    {
        assertTrue(RecipeCategorySearch.matches("  CREATE:FAN_  ", "create:fan_blasting", "批量熔炼"));
        assertTrue(RecipeCategorySearch.matches("熔炼", "create:fan_blasting", "批量熔炼"));
        assertTrue(RecipeCategorySearch.matches("Mechanical Crafting", "create:mechanical_crafting", "Mechanical Crafting"));
        assertTrue(RecipeCategorySearch.matches("  ", "test:unknown", ""));
        assertFalse(RecipeCategorySearch.matches("missing:category", "create:fan_blasting", "批量熔炼"));
    }

    @Test void categoryIdsKeepWorkingUnderTurkishLocale()
    {
        Locale previous = Locale.getDefault();
        try
        {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(RecipeCategorySearch.matches("MILLING", "create:milling", "研磨"));
        }
        finally { Locale.setDefault(previous); }
    }
}
