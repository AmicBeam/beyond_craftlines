package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One-tick queue used when a placed block entity is not ready for discovery yet. */
final class DeferredRegistrationQueue<T>
{
    private final Set<T> queued = new LinkedHashSet<>();

    void schedule(T value)
    {
        queued.add(value);
    }

    List<T> drain()
    {
        List<T> result = new ArrayList<>(queued);
        queued.clear();
        return result;
    }
}
