package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Collection;
import java.util.List;

/** Deterministic selection for machines exposed as more than one JEI recipe type. */
public final class RecipeTypeCycle
{
    private RecipeTypeCycle() {}

    public static String next(Collection<String> candidates, Collection<String> current)
    {
        List<String> ordered = candidates.stream().distinct().sorted().toList();
        if (ordered.isEmpty()) return null;
        if (current.size() != 1) return ordered.getFirst();
        int index = ordered.indexOf(current.iterator().next());
        return index < 0 ? ordered.getFirst() : ordered.get((index + 1) % ordered.size());
    }
}
