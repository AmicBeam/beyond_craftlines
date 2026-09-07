package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Expands declared linked slots together; independent ingredient alternatives remain alternatives. */
public final class LinkedSlotVariants
{
    private LinkedSlotVariants() {}

    public static <T> List<List<List<T>>> expand(List<List<T>> slots, List<List<Integer>> links, int limit)
    {
        int[] parent = new int[slots.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (List<Integer> link : links)
            for (int index : link)
            {
                if (index < 0 || index >= slots.size()) throw new IllegalArgumentException("invalid linked slot");
                parent[root(parent, index)] = root(parent, link.get(0));
            }
        var groups = new LinkedHashMap<Integer, List<Integer>>();
        for (int i = 0; i < parent.length; i++)
            groups.computeIfAbsent(root(parent, i), ignored -> new ArrayList<>()).add(i);
        List<List<List<T>>> variants = new ArrayList<>();
        variants.add(List.copyOf(slots));
        for (List<Integer> group : groups.values())
        {
            if (group.size() < 2) continue;
            int count = slots.get(group.get(0)).size();
            if (count < 1 || group.stream().anyMatch(index -> slots.get(index).size() != count))
                throw new IllegalArgumentException("linked slots contain unequal or empty candidate lists");
            if ((long) count * variants.size() > limit)
                throw new IllegalArgumentException("linked recipe exceeds variant budget");
            List<List<List<T>>> expanded = new ArrayList<>();
            for (List<List<T>> variant : variants)
                for (int choice = 0; choice < count; choice++)
                {
                    List<List<T>> selected = new ArrayList<>(variant);
                    for (int index : group) selected.set(index, List.of(slots.get(index).get(choice)));
                    expanded.add(List.copyOf(selected));
                }
            variants = expanded;
        }
        return List.copyOf(variants);
    }

    private static int root(int[] parent, int index)
    {
        while (parent[index] != index) index = parent[index];
        return index;
    }
}
