package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Vanilla workstation categories that are provisioner-only, plus datapack-enabled virtual recipes. */
public final class VanillaProvisionerRecipeTypes
{
    private static final Set<String> PROVISIONER_ONLY = Set.of(
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
    { return type != null && (PROVISIONER_ONLY.contains(type.toString()) || isJeiOnly(type)); }

    public static boolean isJeiOnly(Object type)
    { return type != null && (!JeiOnlyRecipeTypeRegistry.serverRecipeValidationEnabled()
            || JeiOnlyRecipeTypeRegistry.contains(type)); }

    public static String categoryForBlock(Object blockId)
    { return blockId == null ? null : CATEGORY_BY_BLOCK.get(blockId.toString()); }

    public static <T> Set<T> accepted(Set<T> requested, Set<T> mapped)
    {
        LinkedHashSet<T> accepted = new LinkedHashSet<>(mapped);
        requested.stream().filter(VanillaProvisionerRecipeTypes::isProvisionerOnly).forEach(accepted::add);
        return Set.copyOf(accepted);
    }

    public static <T> Set<T> executable(Set<T> requested)
    {
        return requested.stream().filter(type -> !isJeiOnly(type))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static <T> boolean acceptsAll(Set<T> requested, Set<T> mapped)
    { return accepted(requested, mapped).size() == requested.size(); }

    public static <T> Set<String> provisionerFamilies(Set<T> requested, Set<String> mapped)
    {
        LinkedHashSet<String> families = new LinkedHashSet<>(mapped);
        for (T type : requested)
        {
            String id = String.valueOf(type);
            if (isJeiOnly(type)) families.add(id);
            else if ("minecraft:smithing".equals(id)) families.add("minecraft:smithing");
            else if ("minecraft:stonecutting".equals(id)) families.add("stonecutting");
        }
        return Set.copyOf(families);
    }

    public static Set<String> familiesForType(Object type, Set<String> mapped)
    { return isJeiOnly(type) ? Set.of(type.toString()) : mapped; }
}
