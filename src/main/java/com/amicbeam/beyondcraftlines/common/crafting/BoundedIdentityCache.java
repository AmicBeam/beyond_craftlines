package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.function.Function;

/** Small synchronized FIFO cache whose keys are compared by object identity and strongly bounded. */
final class BoundedIdentityCache<K, V>
{
    private final int maximumSize;
    private final IdentityHashMap<K, V> values = new IdentityHashMap<>();
    private final ArrayDeque<K> insertionOrder = new ArrayDeque<>();

    BoundedIdentityCache(int maximumSize)
    {
        if (maximumSize < 1) throw new IllegalArgumentException("maximumSize must be positive");
        this.maximumSize = maximumSize;
    }

    synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> factory)
    {
        V cached = values.get(key);
        if (cached != null) return cached;
        V created = factory.apply(key);
        values.put(key, created);
        insertionOrder.addLast(key);
        while (values.size() > maximumSize) values.remove(insertionOrder.removeFirst());
        return created;
    }

    synchronized void clear()
    {
        values.clear();
        insertionOrder.clear();
    }

    synchronized int size() { return values.size(); }
    int maximumSize() { return maximumSize; }
}
