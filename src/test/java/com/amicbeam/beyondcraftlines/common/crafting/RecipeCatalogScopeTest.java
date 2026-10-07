package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class RecipeCatalogScopeTest
{
    @Test void fullPreloadIncludesUnboundTypesAndNativeFurnaceFamilies()
    {
        Set<String> full = RecipeCatalogScope.fullFamilies(Set.of("create:mixing", "minecraft:furnace"));
        assertTrue(full.containsAll(Set.of("crafting", "smelting", "blasting", "smoking",
                "minecraft:smithing", "minecraft:stonecutting", "create:mixing", "minecraft:furnace")));
        assertSame(full, RecipeCatalogScope.select(true, Set.of(), full));
        assertSame(full, RecipeCatalogScope.select(true, Set.of("smelting"), full));
        assertSame(full, RecipeCatalogScope.select(true, Set.of("create:mixing"), full));
    }

    @Test void disablingFullPreloadUsesTheNetworkScope()
    {
        Set<String> full = RecipeCatalogScope.fullFamilies(Set.of("create:mixing"));
        assertEquals(Set.of("smelting"), RecipeCatalogScope.select(false, Set.of("smelting"), full));
        assertEquals(Set.of(), RecipeCatalogScope.select(false, Set.of(), full));
    }
}
