package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-tick indexes for recipe-family-aware network claims and machine reservations. */
final class RuntimeOrderIndex<N, M>
{
    private static final String CRAFTING = "crafting";
    private final Map<N, List<Set<String>>> networkClaims = new HashMap<>();
    private final Set<M> occupiedMachines = new HashSet<>();

    boolean claimNetwork(N network, Set<String> recipeFamilies, int maximumConcurrentOrders)
    {
        if (maximumConcurrentOrders < 1)
            throw new IllegalArgumentException("maximum concurrent orders must be positive");
        List<Set<String>> claims = networkClaims.computeIfAbsent(network, ignored -> new ArrayList<>());
        if (claims.size() >= maximumConcurrentOrders) return false;
        Set<String> exclusiveFamilies = new HashSet<>(recipeFamilies);
        exclusiveFamilies.remove(CRAFTING);
        if (claims.stream().anyMatch(claim -> claim.stream().anyMatch(exclusiveFamilies::contains)))
            return false;
        claims.add(Set.copyOf(exclusiveFamilies));
        return true;
    }

    void occupyMachine(M machine) { occupiedMachines.add(machine); }
    void releaseMachine(M machine) { occupiedMachines.remove(machine); }
    boolean isMachineOccupied(M machine) { return occupiedMachines.contains(machine); }
}
