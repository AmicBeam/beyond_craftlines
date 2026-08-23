package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.ProvisionerInputGroupSelection;

import java.util.Comparator;
import java.util.List;

/** Pure endpoint preference shared by grouped direct-machine and provisioner dispatch. */
final class InputGroupRouteLogic
{
    enum Kind { DIRECT_MACHINE, PROVISIONER }

    record Candidate<T>(T endpoint, Kind kind, int priority, String orderKey)
    {
        Candidate
        {
            if (endpoint == null || kind == null || orderKey == null)
                throw new IllegalArgumentException("invalid grouped endpoint");
        }
    }

    private InputGroupRouteLogic() {}

    /** Exact group bindings beat wildcards; equally exact direct machines beat provisioners. */
    static <T> List<Candidate<T>> preferred(List<Candidate<T>> candidates)
    {
        List<Candidate<T>> sorted = candidates.stream()
                .filter(value -> value.priority() < ProvisionerInputGroupSelection.REJECTED_PRIORITY)
                .sorted(Comparator.comparingInt(Candidate<T>::priority)
                        .thenComparingInt(value -> value.kind() == Kind.DIRECT_MACHINE ? 0 : 1)
                        .thenComparing(Candidate<T>::orderKey)).toList();
        if (sorted.isEmpty()) return List.of();
        Candidate<T> first = sorted.getFirst();
        if (first.kind() == Kind.PROVISIONER) return List.of(first);
        return sorted.stream().filter(value -> value.priority() == first.priority()
                && value.kind() == Kind.DIRECT_MACHINE).toList();
    }
}
