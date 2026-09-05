package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes;

import java.util.Set;

/** Keeps a shared recipe index independent from each network's currently bound machine families. */
public final class RecipeIndexVisibility
{
    private RecipeIndexVisibility() {}

    public static boolean includes(String family, Set<String> availableFamilies)
    { return "crafting".equals(family) || availableFamilies.contains(family); }

    /**
     * Native recipes require an explicit network proxy, while a JEI virtual recipe already
     * represents an executable bound-machine category. Both remain scoped to this network.
     */
    public static boolean includesPlanningRecipe(String family, boolean virtual,
                                                 Set<String> availableFamilies)
    {
        return (virtual || VanillaProvisionerRecipeTypes.isPotentialNetworkExecutable(family))
                && includes(family, availableFamilies);
    }
}
