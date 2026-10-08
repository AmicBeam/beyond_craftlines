package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class ClientPlanningCatalogCachePipelineTest
{
    @TempDir Path directory;

    @Test void reportsMissWithoutBlockingTheCallingThread()
    {
        var job = ClientPlanningCatalogCache.loadAsync(directory.resolve("missing.dat"), List.of("a"), 7L);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!job.terminalWithoutCatalog()) Thread.sleep(1L);
        });
        assertFalse(job.complete());
        assertEquals(7L, job.generation());
        assertEquals("miss", job.stateName());
        assertEquals("file_missing", job.reason());
    }

    @Test void restoresAValidEmptyCatalogAndHonorsCancellation() throws Exception
    {
        List<String> ids = List.of();
        Path cache = directory.resolve("catalog.dat");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(cache)))))
        {
            output.writeInt(ClientPlanningCatalogCache.MAGIC);
            output.writeInt(ClientPlanningCatalogCache.VERSION);
            byte[] fingerprint = ClientPlanningCatalogCache.fingerprint(ids).getBytes(StandardCharsets.UTF_8);
            output.writeInt(fingerprint.length);
            output.write(fingerprint);
            output.writeInt(0);
        }
        var hit = ClientPlanningCatalogCache.loadAsync(cache, ids, 9L);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!hit.complete() && !hit.terminalWithoutCatalog())
            {
                hit.advance(null, 1_000_000L);
                Thread.sleep(1L);
            }
        });
        assertTrue(hit.complete());
        assertTrue(hit.catalog().recipes().isEmpty());

        var cancelled = ClientPlanningCatalogCache.loadAsync(cache, ids, 10L);
        cancelled.cancel();
        assertTrue(cancelled.terminalWithoutCatalog());
        assertEquals("cancelled", cancelled.stateName());
    }
    @Test void unchangedRecipeIdsReuseAfterMemoryInvalidation() throws Exception
    {
        List<String> ids = List.of("pack:changed_recipe");
        Path cache = directory.resolve("stale.dat");
        String old = ClientPlanningCatalogCache.fingerprint(ids);
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(cache))))
        {
            output.writeInt(ClientPlanningCatalogCache.MAGIC);
            output.writeInt(ClientPlanningCatalogCache.VERSION);
            byte[] fingerprint = old.getBytes(StandardCharsets.UTF_8);
            output.writeInt(fingerprint.length);
            output.write(fingerprint);
            output.writeInt(0);
        }
        ClientPlanningCatalogWarmup.invalidate();
        assertEquals(old, ClientPlanningCatalogCache.fingerprint(ids));
        var hit = ClientPlanningCatalogCache.loadAsync(cache, ids, 12L);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!hit.complete() && !hit.terminalWithoutCatalog())
            {
                hit.advance(null, 1_000_000L);
                Thread.sleep(1L);
            }
        });
        assertTrue(hit.complete());

        var changedIds = ClientPlanningCatalogCache.loadAsync(cache, List.of("pack:other_recipe"), 13L);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!changedIds.complete() && !changedIds.terminalWithoutCatalog())
            {
                changedIds.advance(null, 1_000_000L);
                Thread.sleep(1L);
            }
        });
        assertTrue(changedIds.complete());
        assertFalse(changedIds.exactMatch());
        assertEquals("recipe_ids_changed", changedIds.reason());

        ClientPlanningCatalogCache.invalidate(cache);
        assertFalse(Files.exists(cache));
        var reloading = ClientPlanningCatalogCache.loadAsync(cache, ids, 14L);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!reloading.terminalWithoutCatalog()) Thread.sleep(1L);
        });
        assertEquals("miss", reloading.stateName());
        assertEquals("file_missing", reloading.reason());
    }

    @Test void savesToDiskAndRestoresThroughTheRealAsyncPipeline() throws Exception
    {
        Path cache = directory.resolve("saved.dat");
        var catalog = new com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner.Catalog(List.of());
        var save = ClientPlanningCatalogCache.saveAsync(null, cache, List.of("pack:recipe"), catalog);
        assertNotNull(save);
        save.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(Files.isRegularFile(cache));
        assertFalse(Files.exists(cache.resolveSibling("saved.dat.tmp")));
        var hit = ClientPlanningCatalogCache.loadAsync(cache, List.of("pack:recipe"), 21L);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!hit.complete() && !hit.terminalWithoutCatalog())
            {
                hit.advance(null, 1_000_000L);
                Thread.sleep(1L);
            }
        });
        assertTrue(hit.complete(), hit.reason());
    }

    @Test void restoresLegacyPlanningRecordsWithMigratedDescriptorIds() throws Exception
    {
        CacheTestKeys.register();
        var oldId = net.minecraft.resources.Identifier.parse("beyond_craftlines:jei_virtual/00000000-0000-0000-0000-000000000001");
        var anotherOldId = net.minecraft.resources.Identifier.parse("beyond_craftlines:jei_virtual/00000000-0000-0000-0000-000000000003");
        var newId = net.minecraft.resources.Identifier.parse("beyond_craftlines:jei_virtual/00000000-0000-0000-0000-000000000002");
        var recipe = new com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner.Recipe(
                oldId, "test:machine", CacheTestKeys.key("output"), 1L,
                com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, List.of());
        var duplicate = new com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner.Recipe(
                anotherOldId, recipe.family(), recipe.output(), recipe.outputCount(), recipe.outputMatch(), recipe.slots());
        var catalog = new com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner.Catalog(List.of(recipe, duplicate));
        Path path = directory.resolve("migration.dat");
        var save = ClientPlanningCatalogCache.saveWithRegistryAsync(net.minecraft.core.RegistryAccess.EMPTY,
                path, List.of(oldId.toString(), anotherOldId.toString()), catalog);
        assertNotNull(save);
        save.get(5, java.util.concurrent.TimeUnit.SECONDS);
        var load = ClientPlanningCatalogCache.loadAsync(path, List.of(newId.toString()), 28L, java.util.Map.of(oldId, newId, anotherOldId, newId));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!load.complete() && !load.terminalWithoutCatalog())
            {
                load.advance(null, 1_000_000L);
                Thread.sleep(1L);
            }
        });
        assertTrue(load.complete(), load.reason());
        assertEquals(List.of(newId), load.catalog().recipes().stream().map(r -> r.id()).toList());
        assertEquals(recipe.output(), load.catalog().recipes().getFirst().output());
        assertEquals(0, load.removedRecipeIds());
    }

    @Test void identifiesAnIncompatibleFormatInsteadOfAnUnexplainedMiss() throws Exception
    {
        Path cache = directory.resolve("old.dat");
        try (var output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(cache))))
        {
            output.writeInt(ClientPlanningCatalogCache.MAGIC);
            output.writeInt(ClientPlanningCatalogCache.VERSION - 1);
        }
        var job = ClientPlanningCatalogCache.loadAsync(cache, List.of(), 22L);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!job.terminalWithoutCatalog()) Thread.sleep(1L);
        });
        assertEquals("format_version_changed", job.reason());
    }

    @Test void separatesWorldsServersAndMinecraftVersions()
    {
        Path first = ClientPlanningCatalogCache.cachePath(directory, "26.3", "world:first");
        assertEquals(first, ClientPlanningCatalogCache.cachePath(directory, "26.3", "world:first"));
        assertNotEquals(first, ClientPlanningCatalogCache.cachePath(directory, "26.3", "world:second"));
        assertNotEquals(first, ClientPlanningCatalogCache.cachePath(directory, "26.3", "server:first"));
        assertNotEquals(first, ClientPlanningCatalogCache.cachePath(directory, "26.2", "world:first"));
        assertNotEquals(first, ClientPlanningCatalogCache.cachePath(directory, "1.21.1", "world:first"));
        assertTrue(first.startsWith(directory));
    }

}
