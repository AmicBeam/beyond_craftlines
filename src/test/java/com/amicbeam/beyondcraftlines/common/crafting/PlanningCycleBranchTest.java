package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlanningCycleBranchTest
{
    @Test
    void ancestorCycleRejectsOnlyCurrentCandidateAndLeavesSiblingSearchable()
    {
        String selected = null;
        List<String> evaluated = new ArrayList<>();
        for (String candidate : List.of("cyclic", "craftable"))
        {
            String result = PlanningCycleBranch.evaluate("root", () -> {
                if (candidate.equals("cyclic")) throw new PlanningCycleBranch.Cycle();
                return candidate;
            }, fallback -> fallback + "|missing:intermediate");
            evaluated.add(result);
            if (!result.contains("missing:"))
            {
                selected = result;
                break;
            }
        }

        assertEquals(List.of("root|missing:intermediate", "craftable"), evaluated);
        assertEquals("craftable", selected);
    }

    @Test
    void expiredOptimizationBudgetStopsCandidateSearch()
    {
        AtomicLong now = new AtomicLong();
        ClientPlanningBudget budget = new ClientPlanningBudget(10, 5, now::get);
        now.set(5);
        List<String> evaluated = new ArrayList<>();
        boolean foundNonCyclic = false;
        for (String candidate : List.of("cyclic_recipe", "cyclic_tag_member", "nether_quartz"))
        {
            if (!PlanningBranches.shouldTryCandidate(foundNonCyclic, budget)) break;
            var result = PlanningCycleBranch.evaluateWithStatus("root", () -> {
                if (candidate.startsWith("cyclic")) throw new PlanningCycleBranch.Cycle();
                return candidate;
            }, ignored -> "cycle");
            evaluated.add(candidate);
            foundNonCyclic |= !result.cyclic();
        }
        assertEquals(List.of(), evaluated);
    }
}
