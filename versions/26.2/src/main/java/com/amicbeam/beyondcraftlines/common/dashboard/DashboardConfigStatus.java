package com.amicbeam.beyondcraftlines.common.dashboard;

/** Pure configuration-screen status rules kept separate for unit testing. */
public final class DashboardConfigStatus
{
    public static final String RECIPE_UNCONFIGURED = "dashboard_recipe_unconfigured";

    private DashboardConfigStatus() {}

    public static boolean recipeConfiguredAfterTargetChange(boolean configured, boolean targetChanged)
    { return configured && !targetChanged; }

    public static String visibleError(boolean recipeConfigured, String runtimeError)
    {
        if (runtimeError != null && !runtimeError.isBlank()) return runtimeError;
        return recipeConfigured ? "" : RECIPE_UNCONFIGURED;
    }
}
