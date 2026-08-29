package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Map;
import java.util.Set;

/** Classifies JEI categories for the manual picker without hiding compatibility candidates. */
public final class ManualRecipeTypeVisibility
{
    private ManualRecipeTypeVisibility() {}

    public static Set<String> visible(Set<String> jeiTypes, Set<String> loadedFamilies,
                                      Map<String, Set<String>> aliases,
                                      Map<String, Set<String>> verifiedHints,
                                      boolean debugMappings)
    { return Set.copyOf(jeiTypes); }

    public static boolean usesCompatibilityMode(String jeiType, Set<String> loadedFamilies,
                                                Map<String, Set<String>> aliases,
                                                Map<String, Set<String>> verifiedHints)
    {
        return !JeiRecipeFamilyMappings.resolve(Set.of(jeiType), loadedFamilies, aliases, verifiedHints)
                .jeiTypes().contains(jeiType);
    }

    /** Compatibility name retained for callers; the picker now always exposes all JEI categories. */
    public static Set<String> visibleOrAllWhenUnresolved(Set<String> jeiTypes,
                                                         Set<String> loadedFamilies,
                                                         Map<String, Set<String>> aliases,
                                                         Map<String, Set<String>> verifiedHints,
                                                         boolean debugMappings)
    {
        return visible(jeiTypes, loadedFamilies, aliases, verifiedHints, debugMappings);
    }
}
