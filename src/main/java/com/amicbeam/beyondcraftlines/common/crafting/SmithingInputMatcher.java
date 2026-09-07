package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Pure ordered matching shared by version-facing vanilla smithing ingredient adapters. */
final class SmithingInputMatcher
{
    private SmithingInputMatcher() {}

    static <T> List<List<T>> ordered(Iterable<T> values, List<Predicate<T>> slots)
    {
        List<List<T>> matches = new ArrayList<>();
        slots.forEach(ignored -> matches.add(new ArrayList<>()));
        for (T value : values)
            for (int slot = 0; slot < slots.size(); slot++)
                if (slots.get(slot).test(value)) matches.get(slot).add(value);
        return matches.stream().map(List::copyOf).toList();
    }
}
