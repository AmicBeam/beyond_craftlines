package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure exact-key inventory state maintained from insert/extract deltas. */
final class IncrementalStock<K>
{
    private final LinkedHashMap<K, Long> amounts = new LinkedHashMap<>();
    private long revision = 1;

    void replace(Map<K, Long> values)
    {
        amounts.clear();
        values.forEach((key, amount) -> { if (key != null && amount != null && amount > 0) amounts.put(key, amount); });
        advanceRevision();
    }

    void apply(K key, long amount, boolean inserted)
    {
        if (key == null || amount <= 0) return;
        long current = amounts.getOrDefault(key, 0L);
        long updated = inserted ? SaturatingLongMath.add(current, amount) : Math.max(0, current - amount);
        if (updated == 0) amounts.remove(key); else amounts.put(key, updated);
        advanceRevision();
    }

    Map<K, Long> snapshot() { return new LinkedHashMap<>(amounts); }
    long revision() { return revision; }

    private void advanceRevision()
    { revision = revision == Long.MAX_VALUE ? 1 : revision + 1; }
}
