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

    /** Always permits the first preferred candidate; alternatives require an active optimization budget. */
    static boolean shouldTryCandidate(boolean hasTriedCandidate, ClientPlanningBudget budget)
    { return hasTriedCandidate ? budget.canOptimize() : budget.canSearch(); }
}
