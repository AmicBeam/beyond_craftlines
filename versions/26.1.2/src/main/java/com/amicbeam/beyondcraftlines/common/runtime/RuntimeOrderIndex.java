package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.HashSet;
import java.util.Set;

/** Per-tick indexes for FIFO network claims and machine reservations. */
final class RuntimeOrderIndex<N, M>
{
    private final Set<N> claimedNetworks = new HashSet<>();
    private final Set<M> occupiedMachines = new HashSet<>();

    boolean claimNetwork(N network) { return claimedNetworks.add(network); }
    void occupyMachine(M machine) { occupiedMachines.add(machine); }
    boolean isMachineOccupied(M machine) { return occupiedMachines.contains(machine); }
}
