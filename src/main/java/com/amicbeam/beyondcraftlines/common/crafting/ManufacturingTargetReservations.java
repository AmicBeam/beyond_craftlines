package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.Map;
import java.util.function.BiPredicate;

/** Removes only the exact manufactured output from input reservations, never component variants. */
public final class ManufacturingTargetReservations
{
    private ManufacturingTargetReservations() {}

    public static void removeFinalOutput(Map<IStackKey<?>, Long> reserved,
                                         IStackKey<?> target, boolean targetIsReusableSeed)
    { removeFinalOutput(reserved, target, targetIsReusableSeed, StackKeyMatch::exact); }

    static <K> void removeFinalOutput(Map<K, Long> reserved, K target,
                                      boolean targetIsReusableSeed, BiPredicate<K, K> exact)
    {
        if (!targetIsReusableSeed)
            reserved.entrySet().removeIf(entry -> exact.test(target, entry.getKey()));
    }
}
