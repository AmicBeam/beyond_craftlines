package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderRetentionTest
{
    @Test
    void displayedTerminalOrderExpiresAtFiveMinutes()
    {
        long finished = 12_000;
        assertFalse(OrderRetention.expired(RecipeOrderJob.Status.COMPLETE,
                finished, finished + OrderRetention.DISPLAY_TICKS - 1));
        assertTrue(OrderRetention.expired(RecipeOrderJob.Status.COMPLETE,
                finished, finished + OrderRetention.DISPLAY_TICKS));
        assertTrue(OrderRetention.expired(RecipeOrderJob.Status.CANCELLED,
                finished, finished + OrderRetention.DISPLAY_TICKS));
    }

    @Test
    void activeAndFailedOrdersAreNotAffected()
    {
        long now = 100_000;
        assertFalse(OrderRetention.expired(RecipeOrderJob.Status.RUNNING, 1, now));
        assertFalse(OrderRetention.expired(RecipeOrderJob.Status.ERROR, 1, now));
    }

    @Test
    void legacyDisplayedTerminalOrderWithoutTimestampExpires()
    {
        assertTrue(OrderRetention.expired(RecipeOrderJob.Status.CANCELLED, 0, 100));
    }
}
