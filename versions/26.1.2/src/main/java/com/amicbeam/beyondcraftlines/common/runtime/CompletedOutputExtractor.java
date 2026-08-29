package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.PlanningSnapshotService;
import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.ArrayList;
import java.util.List;

final class CompletedOutputExtractor
{
    private CompletedOutputExtractor() {}

    static List<KeyAmount> extract(int networkId, UnifiedStorage storage,
                                   IStackKey<?> expected, long amount)
    {
        if (amount <= 0) return List.of();
        List<KeyAmount> extracted = new ArrayList<>();
        long remaining = take(storage, expected, amount, extracted);
        if (remaining > 0 && expected instanceof ItemStackKey expectedItem)
            for (PlanningSnapshotService.ComponentEntry entry :
                    PlanningSnapshotService.capture(networkId).componentEntries())
            {
                if (remaining <= 0) break;
                if (!(entry.key() instanceof ItemStackKey actualItem)
                        || !expectedItem.getSource().equals(actualItem.getSource())
                        || StackKeyMatch.exact(expected, entry.key())) continue;
                remaining = take(storage, entry.key(), remaining, extracted);
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
