package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tracks one-time category materialization within a single JEI runtime generation. */
public final class RecipeTypeWarmupTracker<T>
{
    private final Set<T> active = new LinkedHashSet<>();
    private final Set<T> requested = new LinkedHashSet<>();
    private final Set<T> complete = new LinkedHashSet<>();

    public List<T> activate(Collection<T> types)
    {
        active.addAll(types);
        return request(types);
    }

    public List<T> request(Collection<T> types)
    {
        return types.stream().filter(requested::add).toList();
    }

    public void complete(T type) { complete.add(type); }
    public boolean ready(Collection<T> types) { return complete.containsAll(types); }
    public Set<T> activeTypes() { return Set.copyOf(active); }

    public void clear()
    {
        active.clear();
        requested.clear();
        complete.clear();
    }
}
