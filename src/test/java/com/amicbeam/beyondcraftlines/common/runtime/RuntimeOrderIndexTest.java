package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeOrderIndexTest
{
    @Test
    void overlappingRecipeFamiliesKeepFifoOrder()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();

        assertTrue(index.claimNetwork(7, Set.of("smelting", "create:mixing"), 4));
        assertFalse(index.claimNetwork(7, Set.of("create:mixing"), 4));
        assertFalse(index.claimNetwork(7, Set.of("smelting"), 4));
    }

    @Test
    void differentNetworksAreIndependent()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();

        assertTrue(index.claimNetwork(7, Set.of("smelting"), 4));
        assertTrue(index.claimNetwork(8, Set.of("smelting"), 4));
    }

    @Test
    void disjointRecipeFamiliesShareANetwork()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();

        assertTrue(index.claimNetwork(7, Set.of("smelting"), 4));
        assertTrue(index.claimNetwork(7, Set.of("create:mixing"), 4));
    }

    @Test
    void ordinaryCraftingDoesNotConflict()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();

        assertTrue(index.claimNetwork(7, Set.of("crafting", "smelting"), 4));
        assertTrue(index.claimNetwork(7, Set.of("crafting", "create:mixing"), 4));
    }

    @Test
    void concurrentOrderLimitStillAppliesToDisjointFamilies()
    {
        RuntimeOrderIndex<Integer, String> index = new RuntimeOrderIndex<>();

        assertTrue(index.claimNetwork(7, Set.of("smelting"), 2));
        assertTrue(index.claimNetwork(7, Set.of("create:mixing"), 2));
        assertFalse(index.claimNetwork(7, Set.of("create:packing"), 2));
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

        assertTrue(index.claimNetwork(7, Set.of("smelting"), 4));
        assertTrue(index.isMachineOccupied("furnace-a"));
    }
}
