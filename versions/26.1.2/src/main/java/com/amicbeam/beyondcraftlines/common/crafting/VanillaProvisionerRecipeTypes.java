package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Vanilla JEI categories that can label a provisioner but have no server RecipeType to execute. */
public final class VanillaProvisionerRecipeTypes
{
    private static final Set<String> BIND_ONLY = Set.of(
            "minecraft:anvil",
            "minecraft:brewing",
            "minecraft:compostable",
            "minecraft:smithing",
            "minecraft:stonecutting");
    private static final Map<String, String> CATEGORY_BY_BLOCK = Map.ofEntries(
            Map.entry("minecraft:brewing_stand", "minecraft:brewing"),
            Map.entry("minecraft:smithing_table", "minecraft:smithing"),
            Map.entry("minecraft:composter", "minecraft:compostable"),
            Map.entry("minecraft:anvil", "minecraft:anvil"),
            Map.entry("minecraft:chipped_anvil", "minecraft:anvil"),
            Map.entry("minecraft:damaged_anvil", "minecraft:anvil"),
            Map.entry("minecraft:stonecutter", "minecraft:stonecutting"));

    private VanillaProvisionerRecipeTypes() {}

    public static boolean isBindOnly(Object type)
    { return type != null && BIND_ONLY.contains(type.toString()); }

    public static String categoryForBlock(Object blockId)
    { return blockId == null ? null : CATEGORY_BY_BLOCK.get(blockId.toString()); }

    public static <T> Set<T> accepted(Set<T> requested, Set<T> mapped)
    {
        LinkedHashSet<T> accepted = new LinkedHashSet<>(mapped);
        requested.stream().filter(VanillaProvisionerRecipeTypes::isBindOnly).forEach(accepted::add);
        return Set.copyOf(accepted);
    }

    public static <T> Set<T> executable(Set<T> requested)
    {
        return requested.stream().filter(type -> !isBindOnly(type))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static <T> boolean acceptsAll(Set<T> requested, Set<T> mapped)
    { return accepted(requested, mapped).size() == requested.size(); }
}
