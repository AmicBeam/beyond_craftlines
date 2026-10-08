package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;

/** Mutable deterministic stock allocator whose keys may carry identity beyond their coarse item group. */
final class MatchingStock<K, G>
{
    private final Function<K, G> groupOf;
    private final LinkedHashMap<G, LinkedHashMap<K, Amount>> groups;

    MatchingStock(Function<K, G> groupOf, Map<K, Long> supplied)
    {
        this.groupOf = groupOf;
        this.groups = new LinkedHashMap<>();
        supplied.forEach((key, amount) -> { if (key != null && amount != null) addInitial(key, amount); });
    }

    private MatchingStock(Function<K, G> groupOf, LinkedHashMap<G, LinkedHashMap<K, Amount>> groups)
    {
        this.groupOf = groupOf;
        this.groups = groups;
    }

    long consume(G group, Predicate<K> accepts, long requested)
    { return consume(group, accepts, requested, (key, amount) -> {}); }

    long consume(G group, Predicate<K> accepts, long requested, BiConsumer<K, Long> initialConsumer)
    {
        if (requested <= 0) return 0;
        Map<K, Amount> amounts = groups.get(group);
        if (amounts == null) return 0;
        long consumed = 0;
        for (var entry : amounts.entrySet())
        {
            if (!accepts.test(entry.getKey())) continue;
            Amount available = entry.getValue();
            long produced = Math.min(available.produced, requested - consumed);
            available.produced -= produced;
            consumed = SaturatingLongMath.add(consumed, produced);
            long initial = Math.min(available.initial, requested - consumed);
            available.initial -= initial;
            if (initial > 0) initialConsumer.accept(entry.getKey(), initial);
            consumed = SaturatingLongMath.add(consumed, initial);
            if (consumed >= requested) break;
        }
        return consumed;
    }

    long available(G group, Predicate<K> accepts)
    {
        long result = 0;
        Map<K, Amount> amounts = groups.get(group);
        if (amounts == null) return 0;
        for (var entry : amounts.entrySet())
            if (entry.getValue().total() > 0 && accepts.test(entry.getKey()))
                result = SaturatingLongMath.add(result, entry.getValue().total());
        return result;
    }

    long itemsForCapacity(G group,Predicate<K> accepts,long requiredUnits,
                          ToLongFunction<K> capacityPerItem,long fallbackCapacity)
    {
        if(requiredUnits<=0)return 0;long remaining=requiredUnits,items=0;Map<K,Amount> amounts=groups.get(group);
        if(amounts!=null)for(var entry:amounts.entrySet()){
            if(!accepts.test(entry.getKey())||entry.getValue().total()<=0)continue;
            long capacity=Math.max(1,capacityPerItem.applyAsLong(entry.getKey()));
            long needed=remaining/capacity+(remaining%capacity==0?0:1);
            long used=Math.min(entry.getValue().total(),needed);items=SaturatingLongMath.add(items,used);
            long covered=SaturatingLongMath.multiply(used,capacity);if(covered>=remaining)return items;remaining-=covered;
        }
        long capacity=Math.max(1,fallbackCapacity);long created=remaining/capacity+(remaining%capacity==0?0:1);
        return SaturatingLongMath.add(items,created);
    }

    void clear(G group)
    { groups.remove(group); }

    void add(K key, long amount)
    {
        if (amount <= 0) return;
        Amount value = groups.computeIfAbsent(groupOf.apply(key), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new Amount());
        value.produced = SaturatingLongMath.add(value.produced, amount);
    }

    MatchingStock<K, G> copy()
    {
        LinkedHashMap<G, LinkedHashMap<K, Amount>> copied = new LinkedHashMap<>();
        groups.forEach((group, amounts) -> {
            LinkedHashMap<K, Amount> values = new LinkedHashMap<>();
            amounts.forEach((key, amount) -> values.put(key, amount.copy()));
            copied.put(group, values);
        });
        return new MatchingStock<>(groupOf, copied);
    }

    private void addInitial(K key, long amount)
    {
        if (amount <= 0) return;
        Amount value = groups.computeIfAbsent(groupOf.apply(key), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new Amount());
        value.initial = SaturatingLongMath.add(value.initial, amount);
    }

    private static final class Amount
    {
        private long initial;
        private long produced;
        private long total() { return SaturatingLongMath.add(initial, produced); }
        private Amount copy()
        {
            Amount result = new Amount();
            result.initial = initial;
            result.produced = produced;
            return result;
        }
    }
}
