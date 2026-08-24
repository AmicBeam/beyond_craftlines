package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Pure, mod-agnostic mapping from JEI category ids to loaded server recipe type ids. */
public final class JeiRecipeFamilyMappings
{
    private JeiRecipeFamilyMappings() {}

    public static Resolution resolve(Set<String> jeiTypes, Set<String> loadedFamilies)
    { return resolve(jeiTypes, loadedFamilies, Map.of(), Map.of()); }

    public static Resolution resolve(Set<String> jeiTypes, Set<String> loadedFamilies,
                                     Map<String, Set<String>> aliases,
                                     Map<String, Set<String>> verifiedHints)
    {
        LinkedHashSet<String> acceptedTypes = new LinkedHashSet<>();
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (String jeiType : jeiTypes)
        {
            LinkedHashSet<String> matched = new LinkedHashSet<>();
            if (loadedFamilies.contains(jeiType)) matched.add(jeiType);
            if (jeiType.startsWith("minecraft:"))
            {
                String vanillaFamily = jeiType.substring("minecraft:".length());
                if (loadedFamilies.contains(vanillaFamily)) matched.add(vanillaFamily);
            }
            if (matched.isEmpty()) verifiedHints.getOrDefault(jeiType, Set.of()).stream()
                    .filter(loadedFamilies::contains).forEach(matched::add);
            if (matched.isEmpty()) aliases.getOrDefault(jeiType, Set.of()).stream()
                    .filter(loadedFamilies::contains).forEach(matched::add);
            if (matched.isEmpty()) continue;
            acceptedTypes.add(jeiType);
            families.addAll(matched);
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
