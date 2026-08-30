package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class VirtualInputPolicy
{
    private VirtualInputPolicy() {}
    static boolean reusable(boolean catalyst, boolean consumedOutputContainer, boolean allDamageable)
    { return catalyst ? !consumedOutputContainer : allDamageable; }
    static <T> Set<T> ambiguous(Collection<T> identities)
    {
        Map<T, Integer> counts = new HashMap<>();
        identities.forEach(identity -> { if (identity != null) counts.merge(identity, 1, Integer::sum); });
        return counts.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
}
