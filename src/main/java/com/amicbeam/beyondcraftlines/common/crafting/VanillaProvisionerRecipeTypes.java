package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Vanilla workstation category policy for direct machines, provisioners, and network execution. */
public final class VanillaProvisionerRecipeTypes
{
    private static final Set<String> PROVISIONER_ONLY = Set.of(
            "minecraft:anvil",
            "minecraft:compostable",
            "minecraft:smithing",
            "minecraft:stonecutting");
    private static final Set<String> NETWORK_EXECUTABLE = Set.of(
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
    {
        return requested.stream().filter(VanillaProvisionerRecipeTypes::isProxyFamily)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static boolean isNetworkExecutable(Object family, boolean proxyEnabled)
    { return proxyEnabled && isProxyFamily(family); }

    public static boolean isPotentialNetworkExecutable(Object family)
    { return family != null && ("crafting".equals(family.toString()) || isProxyFamily(family)); }

    public static boolean isProxyFamily(Object family)
    { return family != null && NETWORK_EXECUTABLE.contains(family.toString()); }

    public static Set<String> networkExecutableFamilies(boolean proxyEnabled)
    { return proxyEnabled ? NETWORK_EXECUTABLE : Set.of(); }

    public static <T> Set<T> directBindable(Set<T> requested)
    {
        return requested.stream().filter(type -> !isProvisionerOnly(type))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static Set<String> directFamiliesForType(Object type, Set<String> mapped)
    { return type == null ? Set.of() : Set.of(type.toString()); }

    public static <T> boolean acceptsAll(Set<T> requested, Set<T> mapped)
    { return accepted(requested, mapped).size() == requested.size(); }

    public static <T> Set<String> provisionerFamilies(Set<T> requested, Set<String> mapped)
    {
        LinkedHashSet<String> families = new LinkedHashSet<>();
        if (requested.isEmpty()) mapped.stream().map(VanillaProvisionerRecipeTypes::runtimeFamily)
                .forEach(families::add);
        else requested.stream().map(VanillaProvisionerRecipeTypes::runtimeFamily).forEach(families::add);
        return Set.copyOf(families);
    }

    public static Set<String> familiesForType(Object type, Set<String> mapped)
    { return type == null ? Set.of() : Set.of(runtimeFamily(type)); }

    /** Normalizes JEI's furnace category aliases to the runtime families used by order steps. */
    public static String runtimeFamily(Object type)
    {
        return type == null ? "" : com.amicbeam.beyondcraftlines.common.runtime
                .NativeFurnaceRecipeFamilies.executionFamily(type.toString());
    }

    /** Migrates persisted input-group keys while retaining explicit subgroup selections. */
    public static Map<String, Set<String>> normalizeInputGroups(Map<String, Set<String>> stored)
    {
        LinkedHashMap<String, Set<String>> normalized = new LinkedHashMap<>();
        stored.forEach((family, groups) -> normalized.merge(runtimeFamily(family),
                ProvisionerInputGroupSelection.normalizeStored(groups),
                VanillaProvisionerRecipeTypes::mergeInputGroups));
        return Map.copyOf(normalized);
    }

    private static Set<String> mergeInputGroups(Set<String> left, Set<String> right)
    {
        if (left.contains(ProvisionerInputGroupSelection.ALL)
                || right.contains(ProvisionerInputGroupSelection.ALL))
            return Set.of(ProvisionerInputGroupSelection.ALL);
        LinkedHashSet<String> merged = new LinkedHashSet<>(left);
        merged.addAll(right);
        return Set.copyOf(merged);
    }
}
