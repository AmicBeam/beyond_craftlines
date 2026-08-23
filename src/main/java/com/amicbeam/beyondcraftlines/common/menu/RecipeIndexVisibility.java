package com.amicbeam.beyondcraftlines.common.menu;

import java.util.Set;

/** Keeps a shared recipe index independent from each network's currently bound machine families. */
final class RecipeIndexVisibility
{
    private RecipeIndexVisibility() {}

    static boolean includes(String family, Set<String> availableFamilies)
    { return "crafting".equals(family) || availableFamilies.contains(family); }
}
