package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Enforces one shared resolution for each canonical product or ingredient key. */
final class SharedResolutionMap<K, V>
{
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>();

    void put(K key, V value, String duplicateMessage)
    {
        if (values.put(key, value) != null) throw new IllegalArgumentException(duplicateMessage);
    }

    V get(K key) { return values.get(key); }

    Map<K, V> copy() { return Map.copyOf(values); }
}
