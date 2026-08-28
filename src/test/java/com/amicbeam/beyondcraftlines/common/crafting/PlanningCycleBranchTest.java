package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

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
}
