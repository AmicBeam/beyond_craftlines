package com.amicbeam.beyondcraftlines.common.network;

/** Pure freshness rules shared by proposal validation and submission tests. */
final class PlanningFreshness
{
    enum Result { VALID, RECIPES_CHANGED, REQUIRED_MATERIALS_CHANGED }

    private PlanningFreshness() {}

    static boolean recipesChanged(long proposedEpoch, long currentEpoch)
    { return proposedEpoch != currentEpoch; }

    /** Whole-stock revisions are advisory; current component-aware plan materials decide validity. */
    static Result evaluate(long ignoredProposedStockRevision, long ignoredCurrentStockRevision,
                           long proposedRecipeEpoch, long currentRecipeEpoch,
                           boolean currentPlanCraftable)
    {
        if (recipesChanged(proposedRecipeEpoch, currentRecipeEpoch)) return Result.RECIPES_CHANGED;
        return currentPlanCraftable ? Result.VALID : Result.REQUIRED_MATERIALS_CHANGED;
    }
}
