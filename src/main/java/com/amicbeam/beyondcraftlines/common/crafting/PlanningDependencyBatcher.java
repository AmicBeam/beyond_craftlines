package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates repeated recipe slots before dependency recursion. */
final class PlanningDependencyBatcher
{
    private PlanningDependencyBatcher() {}

    static <K> Map<K, Long> aggregate(List<Entry<K>> entries)
    {
        LinkedHashMap<K, Long> result = new LinkedHashMap<>();
        for (Entry<K> entry : entries)
            result.merge(entry.key(), entry.amount(), SaturatingLongMath::add);
        return Collections.unmodifiableMap(result);
    }

    static long inputAmount(boolean reusable, long amountPerCraft, long crafts)
    {
        if (amountPerCraft < 1 || crafts < 1) throw new IllegalArgumentException("invalid planning input amount");
        return reusable ? amountPerCraft : SaturatingLongMath.multiply(crafts, amountPerCraft);
    }

    record Entry<K>(K key, long amount)
    {
        Entry
        {
            if (key == null || amount < 1) throw new IllegalArgumentException("invalid planning dependency");
        }
    }
}
