package com.amicbeam.beyondcraftlines.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Persistent identity independent of the client runtime or login session. */
final class PlanningCatalogCacheIdentity
{
    private PlanningCatalogCacheIdentity() {}

    static String fingerprint(List<String> holderIds)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            holderIds.forEach(id -> {
                digest.update(id.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return java.util.HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException impossible)
        { throw new IllegalStateException(impossible); }
    }

    static Path cachePath(Path gameDirectory, String gameVersion, String scope)
    {
        String identity = fingerprint(List.of(gameVersion, scope));
        return gameDirectory.resolve("config")
                .resolve("beyond_craftlines-planning-catalog-v4").resolve(identity + ".dat");
    }
}
