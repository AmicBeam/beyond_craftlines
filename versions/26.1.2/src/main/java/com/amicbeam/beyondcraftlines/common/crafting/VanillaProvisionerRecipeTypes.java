package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Vanilla workstation categories that are provisioner-only, plus datapack-enabled virtual recipes. */
public final class VanillaProvisionerRecipeTypes
{
    private static final Set<String> PROVISIONER_ONLY = Set.of(
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

    public static boolean isProvisionerOnly(Object type)
    { return type != null && PROVISIONER_ONLY.contains(type.toString()); }

    public static boolean isJeiOnly(Object type)
    { return type != null; }

    public static String categoryForBlock(Object blockId)
    { return blockId == null ? null : CATEGORY_BY_BLOCK.get(blockId.toString()); }

    public static <T> Set<T> accepted(Set<T> requested, Set<T> mapped)
    {
        LinkedHashSet<T> accepted = new LinkedHashSet<>(mapped);
        requested.stream().filter(type -> isProvisionerOnly(type) || isJeiOnly(type)).forEach(accepted::add);
        return Set.copyOf(accepted);
    }

    public static <T> Set<T> executable(Set<T> requested)
    { return Set.of(); }

    public static <T> Set<T> directBindable(Set<T> requested)
    {
        return requested.stream().filter(type -> !isProvisionerOnly(type))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static Set<String> directFamiliesForType(Object type, Set<String> mapped)
    {
        return type == null ? Set.of() : Set.of(type.toString());
    }

    public static <T> boolean acceptsAll(Set<T> requested, Set<T> mapped)
    { return accepted(requested, mapped).size() == requested.size(); }

    public static <T> Set<String> provisionerFamilies(Set<T> requested, Set<String> mapped)
    {
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (T type : requested)
        {
            String id = String.valueOf(type);
            families.add(id);
        }
        return Set.copyOf(families);
    }

    public static Set<String> familiesForType(Object type, Set<String> mapped)
    { return type == null ? Set.of() : Set.of(type.toString()); }
}
