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

    /** Returns only the extra amount needed to raise a recipe tree's shared reusable requirement. */
    static <K> long additionalReusableAmount(Map<K, Long> requirements, K key, long amount)
    {
        if (requirements == null || key == null || amount < 1)
            throw new IllegalArgumentException("invalid reusable planning dependency");
        long previous = requirements.getOrDefault(key, 0L);
        if (amount <= previous) return 0;
        requirements.put(key, amount);
        return amount - previous;
    }

    record Entry<K>(K key, long amount)
    {
        Entry
        {
            if (key == null || amount < 1) throw new IllegalArgumentException("invalid planning dependency");
        }
    }
}
