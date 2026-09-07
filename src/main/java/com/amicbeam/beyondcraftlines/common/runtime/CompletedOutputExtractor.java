package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Extracts only the exact component-aware output requested by the completed order. */
final class CompletedOutputExtractor
{
    private CompletedOutputExtractor() {}

    static List<KeyAmount> extract(UnifiedStorage storage, IStackKey<?> expected, long amount)
    { return extract(storage, expected, amount, key -> com.amicbeam.beyondcraftlines.common.crafting
            .StackKeyMatch.exact(expected, key)); }

    static List<KeyAmount> extract(UnifiedStorage storage, IStackKey<?> expected, long amount,
                                   Predicate<IStackKey<?>> matcher)
    {
        if (amount <= 0) return List.of();
        List<KeyAmount> extracted = new ArrayList<>();
        long remaining = amount;
        for (KeyAmount available : List.copyOf(storage.getStorage()))
        {
            if (remaining <= 0) break;
            if (!matcher.test(available.key())) continue;
            remaining = take(storage, available.key(), remaining, extracted);
        }
        if (remaining == 0) return List.copyOf(extracted);
        extracted.forEach(value -> storage.insert(value.key(), value.amount(), false));
        return List.of();
    }

    private static long take(UnifiedStorage storage, IStackKey<?> key, long remaining,
                             List<KeyAmount> extracted)
    {
        KeyAmount taken = storage.extract(key, remaining, false, false);
        if (!taken.isEmpty())
        {
            extracted.add(taken);
            return Math.max(0, remaining - taken.amount());
        }
        return remaining;
    }
}
