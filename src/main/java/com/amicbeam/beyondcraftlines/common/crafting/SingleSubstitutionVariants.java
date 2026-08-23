package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;

/** Baseline, one-slot alternatives, and uniform alternatives for repeated equivalent slots. */
public final class SingleSubstitutionVariants
{
    private SingleSubstitutionVariants() {}

    public static <T> List<List<T>> from(List<List<T>> options)
    {
        return from(options, java.util.Objects::equals);
    }

    public static <T> List<List<T>> from(List<List<T>> options, BiPredicate<T, T> equivalent)
    { return from(options, equivalent, ignored -> {}); }

    public static <T> List<List<T>> from(List<List<T>> options, BiPredicate<T, T> equivalent,
                                         IntConsumer generationGuard)
    {
        List<T> baseline = new ArrayList<>(options.size());
        for (List<T> slot : options)
        {
            if (slot.isEmpty()) throw new IllegalArgumentException("ingredient option list is empty");
            baseline.add(slot.getFirst());
        }
        java.util.LinkedHashSet<List<T>> variants = new java.util.LinkedHashSet<>();
        variants.add(List.copyOf(baseline));
        generationGuard.accept(variants.size());
        for (int slot = 0; slot < options.size(); slot++)
        {
            for (int candidate = 1; candidate < options.get(slot).size(); candidate++)
            {
                List<T> variant = new ArrayList<>(baseline);
                variant.set(slot, options.get(slot).get(candidate));
                variants.add(List.copyOf(variant));
                generationGuard.accept(variants.size());
            }
        }
        // Fences and sticks repeat the same tag ingredient in several slots. Changing only one
        // slot can never discover an all-birch branch, while a full Cartesian product is too large.
        boolean[] grouped = new boolean[options.size()];
        for (int first = 0; first < options.size(); first++)
        {
            if (grouped[first]) continue;
            List<Integer> equivalentSlots = new ArrayList<>();
            for (int slot = first; slot < options.size(); slot++)
                if (sameOptions(options.get(first), options.get(slot), equivalent)) equivalentSlots.add(slot);
            if (equivalentSlots.size() < 2) continue;
            equivalentSlots.forEach(slot -> grouped[slot] = true);
            for (int candidate = 1; candidate < options.get(first).size(); candidate++)
            {
                List<T> variant = new ArrayList<>(baseline);
                for (int slot : equivalentSlots) variant.set(slot, options.get(slot).get(candidate));
                variants.add(List.copyOf(variant));
                generationGuard.accept(variants.size());
            }
        }
        return List.copyOf(variants);
    }

    private static <T> boolean sameOptions(List<T> left, List<T> right, BiPredicate<T, T> equivalent)
    {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++)
            if (!equivalent.test(left.get(i), right.get(i))) return false;
        return true;
    }
}
