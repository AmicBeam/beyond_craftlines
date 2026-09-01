package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import java.util.ArrayList;
import java.util.List;

final class CompletedOutputExtractor
{
    private CompletedOutputExtractor() {}

    static List<KeyAmount> extract(UnifiedStorage storage, IStackKey<?> expected, long amount)
    {
        if (amount <= 0) return List.of();
        List<KeyAmount> extracted = new ArrayList<>();
        long remaining = take(storage, expected, amount, extracted);
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
