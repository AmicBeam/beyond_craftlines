package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
import java.util.List;

/** Baseline plus every one-slot alternative; deliberately avoids a Cartesian product. */
public final class SingleSubstitutionVariants
{
    private SingleSubstitutionVariants() {}

    public static <T> List<List<T>> from(List<List<T>> options)
    {
        List<T> baseline = new ArrayList<>(options.size());
        for (List<T> slot : options)
        {
            if (slot.isEmpty()) throw new IllegalArgumentException("ingredient option list is empty");
            baseline.add(slot.getFirst());
        }
        List<List<T>> variants = new ArrayList<>();
        variants.add(List.copyOf(baseline));
        for (int slot = 0; slot < options.size(); slot++)
        {
            for (int candidate = 1; candidate < options.get(slot).size(); candidate++)
            {
                List<T> variant = new ArrayList<>(baseline);
                variant.set(slot, options.get(slot).get(candidate));
                variants.add(List.copyOf(variant));
            }
        }
        return List.copyOf(variants);
    }
}
