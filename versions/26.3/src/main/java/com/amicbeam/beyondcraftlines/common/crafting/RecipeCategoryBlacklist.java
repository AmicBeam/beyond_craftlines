package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact viewer category policy; derived Create categories never alias their original recipes. */
public final class RecipeCategoryBlacklist
{
    public static final List<String> DEFAULTS = List.of("create:automatic_shaped", "create:automatic_shapeless");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static volatile Policy configured = policy(DEFAULTS);
    private static volatile Policy synced;
    private RecipeCategoryBlacklist() {}

    public static boolean validList(Object value)
    { return value instanceof List<?> list && list.size() <= 1024 && list.stream().allMatch(entry ->
            entry instanceof String id && id.length() <= 256 && ID.matcher(id).matches()); }

    private static Policy policy(Collection<? extends String> entries)
    {
        List<String> frozen = List.copyOf(entries);
        if (!validList(frozen)) throw new IllegalArgumentException("invalid recipe category blacklist");
        Set<String> categories = Set.copyOf(frozen);
        return new Policy(frozen, categories, categories.stream().map(RecipeCategoryBlacklist::family)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private static Policy serverPolicy()
    {
        List<? extends String> current = CraftlinesConfig.recipeCategoryBlacklist();
        Policy snapshot = configured;
        if (!snapshot.entries().equals(current)) configured = snapshot = policy(current);
        return snapshot;
    }

    private static Policy clientPolicy()
    { Policy snapshot = synced; return snapshot == null ? serverPolicy() : snapshot; }

    public static List<String> configuredEntries() { return serverPolicy().entries(); }
    public static Set<String> clientCategories() { return clientPolicy().categories(); }
    public static boolean clientBlocked(Object category)
    { return category != null && clientPolicy().categories().contains(category.toString()); }
    public static boolean serverBlocked(Object category)
    { return category != null && serverPolicy().categories().contains(category.toString()); }
    public static boolean clientBlockedFamily(String family) { return clientPolicy().families().contains(family); }
    public static boolean serverBlockedFamily(String family) { return serverPolicy().families().contains(family); }
    public static void applySynced(Collection<String> entries) { synced = policy(entries); }
    public static void clearSynced() { synced = null; }

    static boolean blocksFamily(Collection<String> categories, String family)
    { return policy(categories).families().contains(family); }

    private static String family(String category)
    {
        return switch (category)
        {
            case "minecraft:crafting" -> "crafting";
            case "minecraft:furnace", "minecraft:smelting" -> "smelting";
            case "minecraft:blasting" -> "blasting";
            case "minecraft:smoking" -> "smoking";
            default -> category;
        };
    }
    private record Policy(List<String> entries, Set<String> categories, Set<String> families) {}
}
