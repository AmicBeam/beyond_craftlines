package com.amicbeam.beyondcraftlines.common.crafting;

/** Stable ordering for automatically resolved recipe branches. Lower ranks are preferred. */
record PlanningCandidateRank(long missing, int steps, String recipeId)
        implements Comparable<PlanningCandidateRank>
{
    PlanningCandidateRank
    {
        if (missing < 0 || steps < 0 || recipeId == null)
            throw new IllegalArgumentException("invalid planning candidate rank");
    }

    @Override
    public int compareTo(PlanningCandidateRank other)
    {
        int result = Long.compare(missing, other.missing);
        if (result != 0) return result;
        result = Integer.compare(steps, other.steps);
        if (result != 0) return result;
        return recipeId.compareTo(other.recipeId);
    }
}
