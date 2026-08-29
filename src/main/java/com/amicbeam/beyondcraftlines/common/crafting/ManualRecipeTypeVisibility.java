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

    /**
     * Keeps the manual picker usable when client JEI categories exist but the synchronized
     * family mapping is temporarily empty. The server still validates the selected category
     * and its representative recipe hints before configuring the provisioner.
     */
    public static Set<String> visibleOrAllWhenUnresolved(Set<String> jeiTypes,
                                                         Set<String> loadedFamilies,
                                                         Map<String, Set<String>> aliases,
                                                         Map<String, Set<String>> verifiedHints,
                                                         boolean debugMappings)
    {
        Set<String> visible = visible(jeiTypes, loadedFamilies, aliases, verifiedHints, debugMappings);
        return visible.isEmpty() ? Set.copyOf(jeiTypes) : visible;
    }
}
