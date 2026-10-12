package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Deterministic preferred-first ranking plus conservative reachability filtering.
 * Filtering never drops a preferred candidate, a lone candidate, or every candidate.
 */
final class PlanningRouteOrder
{
    private PlanningRouteOrder() {}

    static <T> List<T> order(List<T> candidates, Function<T, String> idOf, String preferredId,
                             ToIntFunction<T> chainCost, Predicate<T> reachable)
    {
        if (candidates.size() <= 1) return candidates;
        List<T> preferred = new ArrayList<>();
        List<T> rest = new ArrayList<>();
        boolean keptReachable = false;
        for (T candidate : candidates)
        {
            String id = idOf.apply(candidate);
            if (preferredId != null && preferredId.equals(id))
            {
                preferred.add(candidate);
                continue;
            }
            if (reachable.test(candidate))
            {
                keptReachable = true;
                rest.add(candidate);
            }
        }
        if (preferred.isEmpty() && !keptReachable) return candidates;
        rest.sort(Comparator.comparingInt(chainCost).thenComparing(idOf));
        if (preferred.isEmpty()) return List.copyOf(rest);
        preferred.sort(Comparator.comparingInt(chainCost).thenComparing(idOf));
        List<T> ordered = new ArrayList<>(rest.size() + preferred.size());
        ordered.addAll(preferred);
        ordered.addAll(rest);
        return List.copyOf(ordered);
    }

    static boolean recipeReachable(boolean everySlotHasStockedOrProducibleCandidate)
    {
        return everySlotHasStockedOrProducibleCandidate;
    }

    static int unstockedSlotCount(int unstockedSlots)
    {
        return Math.max(0, unstockedSlots);
    }
}
