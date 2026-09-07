package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/** Finds map values with an explicit symmetric/component-aware key policy instead of Map equality. */
final class SymmetricMapLookup
{
    private SymmetricMapLookup() {}

    static <K, V> List<V> first(Map<K, List<V>> values, K requested, BiPredicate<K, K> matches)
    {
        for (var entry : values.entrySet())
            if (matches.test(requested, entry.getKey())) return entry.getValue();
        return List.of();
    }
}
