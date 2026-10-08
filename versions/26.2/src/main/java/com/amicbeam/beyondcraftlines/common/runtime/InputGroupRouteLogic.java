package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.ProvisionerInputGroupSelection;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

/** Pure endpoint preference shared by grouped direct-machine and provisioner dispatch. */
final class InputGroupRouteLogic
{
    enum Kind { DIRECT_MACHINE, PROVISIONER }

    record ResourceChannel<T>(T endpoint, String stackType) {}

    record Candidate<T>(T endpoint, Kind kind, int groupPriority, int endpointPriority, String orderKey)
    {
        Candidate
        {
            if (endpoint == null || kind == null || orderKey == null)
                throw new IllegalArgumentException("invalid grouped endpoint");
        }
    }

    private InputGroupRouteLogic() {}

    /** Exact group bindings beat wildcards; then higher configured priorities win. */
    static <T> List<Candidate<T>> preferred(List<Candidate<T>> candidates)
    {
        List<Candidate<T>> sorted = candidates.stream()
                .filter(value -> value.groupPriority() < ProvisionerInputGroupSelection.REJECTED_PRIORITY)
                .sorted(Comparator.comparingInt(Candidate<T>::groupPriority)
                        .thenComparing(Comparator.comparingInt(Candidate<T>::endpointPriority).reversed())
                        .thenComparingInt(value -> value.kind() == Kind.DIRECT_MACHINE ? 0 : 1)
                        .thenComparing(Candidate<T>::orderKey)).toList();
        if (sorted.isEmpty()) return List.of();
        Candidate<T> first = sorted.getFirst();
        if (first.kind() == Kind.PROVISIONER) return List.of(first);
        return sorted.stream().filter(value -> value.groupPriority() == first.groupPriority()
                && value.endpointPriority() == first.endpointPriority()
                && value.kind() == Kind.DIRECT_MACHINE).toList();
    }

    /** A different resource planned for the same machine may be committed first and retried next tick. */
    static boolean canContinuePartialRound(long offered, long present, boolean deferredByResourceConflict)
    { return offered > 0 || present > 0 || deferredByResourceConflict; }

    static <T> ResourceChannel<T> resourceChannel(T endpoint, Object stackType)
    { return new ResourceChannel<>(endpoint, String.valueOf(stackType)); }

    /** Stable intersection used to keep wildcard input groups on one common machine when possible. */
    static <T> List<T> commonEndpoints(List<List<T>> routes)
    {
        if (routes.isEmpty()) return List.of();
        List<T> common = new ArrayList<>(routes.getFirst());
        for (int index = 1; index < routes.size(); index++)
        {
            List<T> route = routes.get(index);
            common.removeIf(endpoint -> !route.contains(endpoint));
        }
        return List.copyOf(common);
    }
}
