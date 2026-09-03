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
}
