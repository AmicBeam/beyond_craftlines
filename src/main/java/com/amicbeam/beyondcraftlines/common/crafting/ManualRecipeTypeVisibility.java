package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Map;
import java.util.Set;

/** Filters JEI's display categories to the recipe families the server can actually execute. */
public final class ManualRecipeTypeVisibility
{
    private ManualRecipeTypeVisibility() {}

    public static Set<String> visible(Set<String> jeiTypes, Set<String> loadedFamilies,
                                      Map<String, Set<String>> aliases,
                                      Map<String, Set<String>> verifiedHints,
                                      boolean debugMappings)
    {
        if (debugMappings) return Set.copyOf(jeiTypes);
        return JeiRecipeFamilyMappings.resolve(
                jeiTypes, loadedFamilies, aliases, verifiedHints).jeiTypes();
    }
}
