package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlanningRouteOrderTest
{
    @Test
    void neverEliminatesAStockedLeafJustBecauseAnotherRouteLooksShorter()
    {
        List<String> ordered = PlanningRouteOrder.order(List.of("stocked", "craft"), value -> value,
                null, value -> value.equals("stocked") ? 0 : 1, value -> true);
        assertTrue(ordered.contains("stocked"));
    }

    @Test
    void keepsPreferredEvenWhenUnreachableFilteringWouldDropIt()
    {
        List<String> ordered = PlanningRouteOrder.order(List.of("costly", "cheap"), value -> value,
                "costly", value -> value.equals("cheap") ? 0 : 9, value -> value.equals("cheap"));
        assertEquals(List.of("costly", "cheap"), ordered);
    }

    @Test
    void doesNotDropEveryCandidateWhenNothingLooksReachable()
    {
        List<String> ordered = PlanningRouteOrder.order(List.of("a", "b"), value -> value,
                null, value -> 1, value -> false);
        assertEquals(List.of("a", "b"), ordered);
    }
    @Test
    void retainsEveryMatchingPreferenceCandidate()
    {
        List<String> ordered = PlanningRouteOrder.order(List.of("stocked", "missing", "other"),
                value -> value.equals("other") ? "other" : "saved_alias", "saved_alias",
                value -> value.equals("stocked") ? 0 : 1, value -> true);
        assertEquals(List.of("stocked", "missing", "other"), ordered);
    }
}
