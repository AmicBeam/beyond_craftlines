package com.amicbeam.beyondcraftlines.common.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class DashboardConfigStatusTest
{
    @Test void unconfiguredDashboardShowsRecipeErrorWhenOpened()
    {
        assertEquals(DashboardConfigStatus.RECIPE_UNCONFIGURED,
                DashboardConfigStatus.visibleError(false, DashboardStockMode.NETWORK, ""));
    }

    @Test void changingTargetInvalidatesConfiguredRecipeAndShowsError()
    {
        boolean configured = DashboardConfigStatus.recipeConfiguredAfterTargetChange(true, true);

        assertFalse(configured);
        assertEquals(DashboardConfigStatus.RECIPE_UNCONFIGURED,
                DashboardConfigStatus.visibleError(configured, DashboardStockMode.NETWORK, ""));
    }
    @Test void containerTransfersDoNotRequireRecipe()
    {
        assertEquals("", DashboardConfigStatus.visibleError(false, DashboardStockMode.CONTAINER, ""));
        assertEquals("dashboard_container_blocked", DashboardConfigStatus.visibleError(
                false, DashboardStockMode.CONTAINER, "dashboard_container_blocked"));
    }
}
