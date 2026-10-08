package com.amicbeam.beyondcraftlines.common.crafting;

/** Stable planner result shared by background search, preview protocol, and UI. */
public enum PlanningOutcome
{
    SEARCHING("searching"), READY("ready"), NO_RECIPE("no_recipe"),
    MISSING_INPUTS("missing_inputs"), CYCLE("cycle"),
    BUDGET_EXHAUSTED("budget_exhausted"), RUNTIME_UNAVAILABLE("runtime_unavailable"), STALE("stale");

    private final String id;
    PlanningOutcome(String id) { this.id = id; }
    public String id() { return id; }
    public boolean craftable() { return this == READY; }

    /** A non-cyclic alternative is more useful than a rejected loop, regardless of leaf counts. */
    public static boolean prefersAlternative(PlanningOutcome current, long currentMissing,
                                             PlanningOutcome alternative, long alternativeMissing)
    {
        if ((current == CYCLE) != (alternative == CYCLE)) return current == CYCLE;
        return alternativeMissing <= currentMissing;
    }

    public static PlanningOutcome byId(String id)
    {
        if (id != null) for (PlanningOutcome value : values()) if (value.id.equals(id)) return value;
        return RUNTIME_UNAVAILABLE;
    }

    public static PlanningOutcome completed(boolean missing, boolean rootNoRecipe,
                                             boolean cycle, boolean budgetExhausted)
    {
        if (!missing) return READY;
        if (rootNoRecipe) return NO_RECIPE;
        if (budgetExhausted) return BUDGET_EXHAUSTED;
        if (cycle) return CYCLE;
        return MISSING_INPUTS;
    }
}
