package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class BoundedIdentityCacheTest
{
    @Test void boundsEntriesAndKeepsIdentitySemantics()
    {
        BoundedIdentityCache<String, Integer> cache = new BoundedIdentityCache<>(32);
        for (int i = 0; i < 64; i++)
        {
            int value = i;
            cache.computeIfAbsent(new String("same"), ignored -> value);
        }
        assertEquals(32, cache.size());

        String shared = new String("key");
        AtomicInteger calls = new AtomicInteger();
        int first = cache.computeIfAbsent(shared, ignored -> calls.incrementAndGet());
        int second = cache.computeIfAbsent(shared, ignored -> calls.incrementAndGet());
        assertEquals(first, second);
        assertEquals(1, calls.get());
        assertNotEquals(0, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
    }
}
