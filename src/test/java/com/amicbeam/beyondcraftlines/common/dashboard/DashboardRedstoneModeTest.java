package com.amicbeam.beyondcraftlines.common.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DashboardRedstoneModeTest
{
    @Test void appliesLevelModes()
    {
        assertTrue(DashboardRedstoneMode.IGNORE.allows(false, false));
        assertTrue(DashboardRedstoneMode.IGNORE.allows(true, false));
        assertTrue(DashboardRedstoneMode.LOW.allows(false, false));
        assertFalse(DashboardRedstoneMode.LOW.allows(true, false));
        assertTrue(DashboardRedstoneMode.HIGH.allows(true, false));
        assertFalse(DashboardRedstoneMode.HIGH.allows(false, false));
    }

    @Test void pulseModeRunsOnlyForALatchedEdge()
    {
        assertTrue(DashboardRedstoneMode.PULSE.allows(false, true));
        assertFalse(DashboardRedstoneMode.PULSE.allows(true, false));
    }
}
