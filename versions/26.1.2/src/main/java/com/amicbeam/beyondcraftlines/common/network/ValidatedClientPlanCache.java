package com.amicbeam.beyondcraftlines.common.network;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides;
import net.minecraft.resources.Identifier;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread-only cache of the latest server-validated client proposal per player. */
final class ValidatedClientPlanCache
{
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private ValidatedClientPlanCache() {}

    static void put(UUID player, Entry entry)
    {
        ENTRIES.values().removeIf(existing -> existing.expiresAt() < entry.expiresAt() - 20 * 30);
        ENTRIES.put(player, entry);
    }

    static Entry consume(UUID player, long nonce, long now)
    {
        Entry entry = ENTRIES.remove(player);
        if (entry == null || entry.nonce() != nonce || entry.expiresAt() < now) return null;
        return entry;
    }

    record Entry(long nonce, int networkId, IStackKey<?> target, long count,
                 long recipeEpoch, long expiresAt, RecipeResolutionOverrides overrides) {}
}
