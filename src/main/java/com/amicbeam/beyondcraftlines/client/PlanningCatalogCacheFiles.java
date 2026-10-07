package com.amicbeam.beyondcraftlines.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Prevents an asynchronous save from undoing a manual cache reload. */
final class PlanningCatalogCacheFiles
{
    private static final Map<Path, Long> INVALIDATIONS = new HashMap<>();

    private PlanningCatalogCacheFiles() {}

    static synchronized long revision(Path path)
    { return INVALIDATIONS.getOrDefault(path, 0L); }

    static synchronized void invalidate(Path path) throws IOException
    {
        INVALIDATIONS.merge(path, 1L, Long::sum);
        Files.deleteIfExists(path);
    }

    enum InstallResult { INSTALLED, INVALIDATED }

    static synchronized InstallResult install(Path path, Path temporary, long revision) throws IOException
    {
        if (revision != revision(path))
        {
            Files.deleteIfExists(temporary);
            return InstallResult.INVALIDATED;
        }
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored)
        { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        return InstallResult.INSTALLED;
    }
}
