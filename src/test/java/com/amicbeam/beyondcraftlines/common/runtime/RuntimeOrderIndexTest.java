package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeOrderIndexTest
{
    @Test
    void onlyFirstOrderClaimsANetwork()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();

        assertTrue(index.claimNetwork(7));
        assertFalse(index.claimNetwork(7));
        assertFalse(index.claimNetwork(7));
    }

    @Test
    void differentNetworksAreIndependent()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();

        assertTrue(index.claimNetwork(7));
        assertTrue(index.claimNetwork(8));
    }

    @Test
    void machineOccupationIsIndexedAndIsolatedByKey()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();
        assertFalse(index.isMachineOccupied("furnace-a"));
        assertFalse(index.isMachineOccupied("furnace-b"));

        index.occupyMachine("furnace-a");

        assertTrue(index.isMachineOccupied("furnace-a"));
        assertFalse(index.isMachineOccupied("furnace-b"));
    }

    @Test
    void networkAndMachineIndexesDoNotInterfere()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();
        index.occupyMachine("furnace-a");

        assertTrue(index.claimNetwork(7));
        assertTrue(index.isMachineOccupied("furnace-a"));
    }
}
