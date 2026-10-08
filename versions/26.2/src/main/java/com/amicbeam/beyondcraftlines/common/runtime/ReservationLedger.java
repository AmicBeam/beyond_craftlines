package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure subtraction logic for immutable order escrow updates. */
final class ReservationLedger
{
    private ReservationLedger() {}

    static <K> LinkedHashMap<K, Long> subtract(Map<K, Long> reserved, Map<K, Long> consumed)
    {
        LinkedHashMap<K, Long> result = new LinkedHashMap<>(reserved);
        for (var entry : consumed.entrySet())
        {
            long available = result.getOrDefault(entry.getKey(), 0L);
            if (entry.getValue() < 0 || available < entry.getValue())
                throw new IllegalStateException("reserved material underflow");
            long left = available - entry.getValue();
            if (left == 0) result.remove(entry.getKey()); else result.put(entry.getKey(), left);
        }
        return result;
    }
}
