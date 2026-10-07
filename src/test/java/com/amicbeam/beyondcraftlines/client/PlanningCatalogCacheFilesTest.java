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
        assertEquals(PlanningCatalogCacheFiles.InstallResult.INVALIDATED,
                PlanningCatalogCacheFiles.install(cache, pending, oldRevision));
        assertFalse(Files.exists(cache));
        assertFalse(Files.exists(pending));

        Files.writeString(pending, "new catalog");
        assertEquals(PlanningCatalogCacheFiles.InstallResult.INSTALLED,
                PlanningCatalogCacheFiles.install(cache, pending, PlanningCatalogCacheFiles.revision(cache)));
        assertEquals("new catalog", Files.readString(cache));
        assertFalse(Files.exists(pending));
    }

    @Test void reloadOnlyRemovesItsOwnScopeAndLargeSavesAreInstalled() throws Exception
    {
        Path first = directory.resolve("first.dat");
        Path second = directory.resolve("second.dat");
        Files.writeString(first, "first");
        Files.writeString(second, "second");
        long secondRevision = PlanningCatalogCacheFiles.revision(second);
        PlanningCatalogCacheFiles.invalidate(first);
        assertEquals("second", Files.readString(second));
        assertEquals(secondRevision, PlanningCatalogCacheFiles.revision(second));

        Path large = directory.resolve("second.dat.tmp");
        long size = 2L * 1024L * 1024L * 1024L + 1;
        try (var channel = java.nio.channels.FileChannel.open(large,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE))
        {
            channel.position(size - 1);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
        }
        assertEquals(PlanningCatalogCacheFiles.InstallResult.INSTALLED,
                PlanningCatalogCacheFiles.install(second, large, secondRevision));
        assertEquals(size, Files.size(second));
        assertFalse(Files.exists(large));
    }
}
