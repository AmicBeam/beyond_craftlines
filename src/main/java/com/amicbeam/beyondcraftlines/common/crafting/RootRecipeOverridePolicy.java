package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Map;

/** Keeps a JEI entry recipe as the automatic root fallback without overriding a later manual choice. */
public final class RootRecipeOverridePolicy
{
    private RootRecipeOverridePolicy() {}

    public static <K, V> void putInitialFallback(Map<K, V> overrides, K target, V initialRecipe)
    {
        if (initialRecipe != null) overrides.putIfAbsent(target, initialRecipe);
    }
}
