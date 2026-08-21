package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderRetentionTest
{
    @Test
    void completedOrderExpiresAtFiveMinutes()
    {
        long finished = 12_000;
        assertFalse(OrderRetention.expiredCompleted(true,
                finished, finished + OrderRetention.COMPLETED_TICKS - 1));
        assertTrue(OrderRetention.expiredCompleted(true,
                finished, finished + OrderRetention.COMPLETED_TICKS));
    }

    @Test
    void activeAndOtherTerminalStatusesAreNotAffected()
    {
        long now = 100_000;
        assertFalse(OrderRetention.expiredCompleted(false, 1, now));
    }

    @Test
    void legacyCompletedOrderWithoutTimestampExpires()
    {
        assertTrue(OrderRetention.expiredCompleted(true, 0, 100));
    }
}
