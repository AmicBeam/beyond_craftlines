package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ClientPlanningCatalogCacheIdentityTest
{
    @Test void fingerprintIsStableAndSeparatesRecipeIds()
    {
        var ids = List.of("pack:first", "pack:second");
        assertEquals(PlanningCatalogCacheIdentity.fingerprint(ids),
                PlanningCatalogCacheIdentity.fingerprint(List.copyOf(ids)));
        assertNotEquals(PlanningCatalogCacheIdentity.fingerprint(ids),
                PlanningCatalogCacheIdentity.fingerprint(List.of("pack:first", "pack:changed")));
        assertNotEquals(PlanningCatalogCacheIdentity.fingerprint(List.of("ab", "c")),
                PlanningCatalogCacheIdentity.fingerprint(List.of("a", "bc")));
    }

    @Test void cacheScopeSurvivesReconnectAndSeparatesWorldsServersAndVersions()
    {
        Path directory = Path.of("cache-test");
        Path first = PlanningCatalogCacheIdentity.cachePath(directory, "1.21.1", "world:first");
        assertEquals(first, PlanningCatalogCacheIdentity.cachePath(directory, "1.21.1", "world:first"));
        assertNotEquals(first, PlanningCatalogCacheIdentity.cachePath(directory, "1.21.1", "world:second"));
        assertNotEquals(first, PlanningCatalogCacheIdentity.cachePath(directory, "1.21.1", "server:first"));
        assertNotEquals(first, PlanningCatalogCacheIdentity.cachePath(directory, "1.20.1", "world:first"));
        assertNotEquals(first, PlanningCatalogCacheIdentity.cachePath(directory, "26.1.2", "world:first"));
        assertTrue(first.startsWith(directory));
    }
}
