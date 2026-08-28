package com.amicbeam.beyondcraftlines.common.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class DashboardConfigStatusTest
{
    @Test void unconfiguredDashboardShowsRecipeErrorWhenOpened()
    {
        assertEquals(DashboardConfigStatus.RECIPE_UNCONFIGURED,
                DashboardConfigStatus.visibleError(false, ""));
    }

    @Test void changingTargetInvalidatesConfiguredRecipeAndShowsError()
    {
        boolean configured = DashboardConfigStatus.recipeConfiguredAfterTargetChange(true, true);

        assertFalse(configured);
        assertEquals(DashboardConfigStatus.RECIPE_UNCONFIGURED,
                DashboardConfigStatus.visibleError(configured, ""));
    }
}
