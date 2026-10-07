package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Full catalog scope stays stable as networks gain or lose machines. */
public final class RecipeCatalogScope
{
    private RecipeCatalogScope() {}

    public static Set<String> select(boolean preloadAll, Collection<String> networkFamilies,
                                     Set<String> fullFamilies)
    { return preloadAll ? fullFamilies : Set.copyOf(networkFamilies); }

    public static Set<String> fullFamilies(Collection<String> jeiCategories)
    {
        LinkedHashSet<String> families = new LinkedHashSet<>(Set.of(
                "crafting", "smelting", "blasting", "smoking",
                "minecraft:smithing", "minecraft:stonecutting"));
        families.addAll(jeiCategories);
        jeiCategories.stream().map(VanillaProvisionerRecipeTypes::runtimeFamily).forEach(families::add);
        return Set.copyOf(families);
    }
}
