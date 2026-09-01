package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/** Presentation invariant: the requested final output is never reported as a missing ingredient. */
public final class MissingMaterialDisplay
{
    private MissingMaterialDisplay() {}

    public static boolean isFinalOutput(IStackKey<?> target, IStackKey<?> candidate)
    { return StackKeyMatch.exact(target, candidate); }

    public static Map<IStackKey<?>, Long> excludingFinalOutput(
            Map<IStackKey<?>, Long> missing, IStackKey<?> target)
    { return excludingFinalOutput(missing, target, StackKeyMatch::exact); }

    static <K> Map<K, Long> excludingFinalOutput(
            Map<K, Long> missing, K target, BiPredicate<K, K> matches)
    {
        LinkedHashMap<K, Long> result = new LinkedHashMap<>();
        missing.forEach((key, amount) -> {
            if (amount != null && amount > 0 && !matches.test(target, key)) result.put(key, amount);
        });
        return Map.copyOf(result);
    }
}
