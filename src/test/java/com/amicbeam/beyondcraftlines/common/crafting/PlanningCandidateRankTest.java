package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlanningCandidateRankTest
{
    @Test
    void prefersCraftabilityBeforeRecipeId()
    {
        PlanningCandidateRank craftable = new PlanningCandidateRank(0, 8, "z:last");
        PlanningCandidateRank missing = new PlanningCandidateRank(1, 1, "a:first");
        assertTrue(craftable.compareTo(missing) < 0);
    }

    @Test
    void usesStepsThenIdAsStableTieBreakers()
    {
        assertTrue(new PlanningCandidateRank(0, 2, "z:last")
                .compareTo(new PlanningCandidateRank(0, 3, "a:first")) < 0);
        assertTrue(new PlanningCandidateRank(0, 2, "a:first")
                .compareTo(new PlanningCandidateRank(0, 2, "z:last")) < 0);
    }
}
