package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class OrderOutputDestinationTest
{
    @Test
    void defaultsUnknownAndMissingValuesToNetwork()
    {
        assertEquals(OrderOutputDestination.NETWORK, OrderOutputDestination.byId(null));
        assertEquals(OrderOutputDestination.NETWORK, OrderOutputDestination.byId("unknown"));
        assertEquals(OrderOutputDestination.CONTAINER, OrderOutputDestination.byId("container"));
    }

    @Test
    void cyclesBetweenNetworkAndInventory()
    {
        assertEquals(OrderOutputDestination.INVENTORY, OrderOutputDestination.NETWORK.next());
        assertEquals(OrderOutputDestination.NETWORK, OrderOutputDestination.INVENTORY.next());
        assertEquals(OrderOutputDestination.NETWORK, OrderOutputDestination.CONTAINER.next());
    }
}
