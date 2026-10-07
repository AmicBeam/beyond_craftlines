package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class PlanningCatalogCacheFilesTest
{
    @TempDir Path directory;

    @Test void manualReloadRejectsAnOlderSaveButAllowsTheReplacement() throws Exception
    {
        Path cache = directory.resolve("catalog.dat");
        Path pending = directory.resolve("catalog.dat.tmp");
        Files.writeString(cache, "old catalog");
        long oldRevision = PlanningCatalogCacheFiles.revision(cache);
        Files.writeString(pending, "old in-flight save");

        PlanningCatalogCacheFiles.invalidate(cache);
        assertFalse(Files.exists(cache));
        PlanningCatalogCacheFiles.install(cache, pending, oldRevision, 1024L);
        assertFalse(Files.exists(cache));
        assertFalse(Files.exists(pending));

        Files.writeString(pending, "new catalog");
        PlanningCatalogCacheFiles.install(cache, pending, PlanningCatalogCacheFiles.revision(cache), 1024L);
        assertEquals("new catalog", Files.readString(cache));
        assertFalse(Files.exists(pending));
    }

    @Test void reloadOnlyRemovesItsOwnScopeAndOversizedSavesKeepExistingCache() throws Exception
    {
        Path first = directory.resolve("first.dat");
        Path second = directory.resolve("second.dat");
        Files.writeString(first, "first");
        Files.writeString(second, "second");
        long secondRevision = PlanningCatalogCacheFiles.revision(second);
        PlanningCatalogCacheFiles.invalidate(first);
        assertEquals("second", Files.readString(second));
        assertEquals(secondRevision, PlanningCatalogCacheFiles.revision(second));

        Path oversized = directory.resolve("second.dat.tmp");
        Files.writeString(oversized, "too large");
        PlanningCatalogCacheFiles.install(second, oversized, secondRevision, 1L);
        assertEquals("second", Files.readString(second));
        assertFalse(Files.exists(oversized));
    }
}
