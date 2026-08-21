package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure, mod-agnostic mapping from JEI category ids to loaded server recipe type ids. */
public final class JeiRecipeFamilyMappings
{
    private JeiRecipeFamilyMappings() {}

    private static String serverFamily(String jeiType)
    {
        return switch (jeiType)
        {
            // Mekanism exposes the two rotary directions as separate JEI categories,
            // while both are backed by the same server-side RecipeType.
            case "mekanism:condensentrating", "mekanism:decondensentrating" -> "mekanism:rotary";
            default -> jeiType.startsWith("minecraft:")
                    ? jeiType.substring("minecraft:".length()) : jeiType;
        };
    }

    public static Resolution resolve(Set<String> jeiTypes, Set<String> loadedFamilies)
    {
        LinkedHashSet<String> acceptedTypes = new LinkedHashSet<>();
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (String jeiType : jeiTypes)
        {
            String family = serverFamily(jeiType);
            if (!loadedFamilies.contains(family)) continue;
            acceptedTypes.add(jeiType);
            families.add(family);
        }
        return new Resolution(Set.copyOf(acceptedTypes), Set.copyOf(families));
    }

    public record Resolution(Set<String> jeiTypes, Set<String> families)
    {
        public Resolution
        {
            jeiTypes = Set.copyOf(jeiTypes);
            families = Set.copyOf(families);
        }

        public boolean isEmpty() { return families.isEmpty(); }
    }
}
