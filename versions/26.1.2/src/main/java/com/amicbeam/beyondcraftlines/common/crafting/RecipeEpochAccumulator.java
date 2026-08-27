package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Order-independent per-family recipe generation accumulated while the server index is built. */
public final class RecipeEpochAccumulator
{
    private final Map<String, Long> familyEpochs = new LinkedHashMap<>();
    private final Map<String, Long> familyEpochSums = new LinkedHashMap<>();
    private final Map<String, Integer> familyRecipeCounts = new LinkedHashMap<>();

    public void add(String family, long identity)
    {
        if (family == null || family.isBlank()) return;
        familyEpochs.merge(family, identity, (left, right) -> left ^ right);
        familyEpochSums.merge(family, identity, Long::sum);
        familyRecipeCounts.merge(family, 1, Integer::sum);
    }

    public long epoch(Set<String> availableFamilies)
    {
        TreeSet<String> families = new TreeSet<>(availableFamilies);
        families.add("crafting");
        long hash = 0xCBF29CE484222325L;
        for (String family : families)
        {
            hash = mix(hash, family);
            hash = mix(hash, Long.toUnsignedString(familyEpochs.getOrDefault(family, 0L)));
            hash = mix(hash, Long.toUnsignedString(familyEpochSums.getOrDefault(family, 0L)));
            hash = mix(hash, Integer.toString(familyRecipeCounts.getOrDefault(family, 0)));
        }
        return hash & Long.MAX_VALUE;
    }

    public Snapshot snapshot()
    { return new Snapshot(Map.copyOf(familyEpochs), Map.copyOf(familyEpochSums),
            Map.copyOf(familyRecipeCounts)); }

    public void restore(Snapshot snapshot)
    {
        familyEpochs.clear();
        familyEpochSums.clear();
        familyRecipeCounts.clear();
        if (snapshot == null) return;
        familyEpochs.putAll(snapshot.xors());
        familyEpochSums.putAll(snapshot.sums());
        familyRecipeCounts.putAll(snapshot.counts());
    }

    public static long mix(long hash, String value)
    {
        if (value == null) return hash;
        for (int i = 0; i < value.length(); i++)
        {
            hash ^= value.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    public record Snapshot(Map<String, Long> xors, Map<String, Long> sums, Map<String, Integer> counts) {}
}
