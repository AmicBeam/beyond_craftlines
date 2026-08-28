package com.amicbeam.beyondcraftlines.common.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AutomaticOrderPolicyTest
{
    @Test void computesOnlyTheMissingQuantity()
    {
        assertEquals(36, AutomaticOrderPolicy.deficit(100, 64));
        assertEquals(0, AutomaticOrderPolicy.deficit(100, 100));
        assertEquals(0, AutomaticOrderPolicy.deficit(100, 120));
    }

    @Test void enforcesThePerNetworkAutomaticOrderLimit()
    {
        assertTrue(AutomaticOrderPolicy.canCreate(9, 10));
        assertFalse(AutomaticOrderPolicy.canCreate(10, 10));
    }

    @Test void limitsDirectNetworkTransferToTheWritableDeficit()
    {
        assertEquals(12, AutomaticOrderPolicy.transferable(36, 12));
        assertEquals(8, AutomaticOrderPolicy.transferable(8, 64));
        assertEquals(0, AutomaticOrderPolicy.transferable(8, 0));
    }
}
