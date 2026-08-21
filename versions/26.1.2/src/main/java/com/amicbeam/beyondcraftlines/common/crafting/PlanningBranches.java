package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.List;

/** Pure branch gate kept separate so deterministic proposal paths remain allocation-free. */
final class PlanningBranches
{
    private PlanningBranches() {}

    static boolean recipesRequireBranches(int candidateCount)
    { return candidateCount > 1; }

    static boolean ingredientsRequireBranches(List<? extends List<?>> options)
    { return options.stream().anyMatch(option -> option.size() > 1); }
}
