package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps JEI category ids to the server recipe families that Craftlines can plan.
 * The mapping is deliberately mod-agnostic: namespaced JEI category ids must
 * equal loaded server RecipeType ids; vanilla ids use Craftlines' short names.
 */
public final class JeiRecipeFamilyRegistry
{
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static volatile Map<String, Set<String>> verifiedHints = Map.of();
    private JeiRecipeFamilyRegistry() {}

    public static Resolution resolve(Set<ResourceLocation> jeiTypes, Set<String> loadedFamilies)
    {
        var raw = JeiRecipeFamilyMappings.resolve(
                jeiTypes.stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.toSet()),
                loadedFamilies, RecipeFamilyAliasRegistry.aliases(), verifiedHints);
        Set<ResourceLocation> acceptedTypes = raw.jeiTypes().stream()
                .map(ResourceLocation::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Resolution(acceptedTypes, raw.families());
    }

    /** Verifies representative recipe ids with the authoritative server recipe manager, then caches them. */
    public static synchronized void verifyAndRemember(ServerLevel level, Collection<RecipeFamilyHint> hints)
    {
        Map<String, LinkedHashSet<String>> building = new HashMap<>();
        verifiedHints.forEach((type, families) -> building.put(type, new LinkedHashSet<>(families)));
        for (RecipeFamilyHint hint : hints.stream().limit(128).toList())
        {
            if (hint == null || hint.jeiType().isBlank() || hint.family().isBlank()
                    || hint.recipeId().isBlank()) continue;
            if (verifiedHints.getOrDefault(hint.jeiType(), Set.of()).contains(hint.family())) continue;
            ResourceLocation recipeId = ResourceLocation.tryParse(hint.recipeId());
            if (recipeId == null) continue;
            var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null || !hint.family().equals(RecipePlanningService.family(holder))) continue;
            building.computeIfAbsent(hint.jeiType(), ignored -> new LinkedHashSet<>()).add(hint.family());
        }
        Map<String, Set<String>> frozen = new HashMap<>();
        building.forEach((type, families) -> frozen.put(type, Set.copyOf(families)));
        verifiedHints = Map.copyOf(frozen);
    }

    public static synchronized void clearVerifiedHints() { verifiedHints = Map.of(); }

    /** Resolves only server-observed RecipeTypes for diagnostics; client-declared family names are ignored. */
    public static Map<String, Set<String>> diagnoseActualFamilies(ServerLevel level,
                                                                  Collection<RecipeFamilyHint> hints,
                                                                  Collection<String> jeiTypes)
    {
        Set<String> requested = Set.copyOf(jeiTypes);
        Map<String, LinkedHashSet<String>> found = new HashMap<>();
        for (RecipeFamilyHint hint : hints.stream().limit(128).toList())
        {
            if (hint == null || !requested.contains(hint.jeiType()) || hint.recipeId().isBlank()) continue;
            ResourceLocation recipeId = ResourceLocation.tryParse(hint.recipeId());
            if (recipeId == null) continue;
            var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null) continue;
            String actual = RecipePlanningService.family(holder);
            if (actual != null && !actual.isBlank())
                found.computeIfAbsent(hint.jeiType(), ignored -> new LinkedHashSet<>()).add(actual);
        }
        Map<String, Set<String>> frozen = new HashMap<>();
        found.forEach((type, families) -> frozen.put(type, Set.copyOf(families)));
        return Map.copyOf(frozen);
    }

    public static void logUnmapped(Collection<?> types, Set<String> loadedFamilies)
    {
        for (Object value : types)
        {
            String type = String.valueOf(value);
            int separator = type.indexOf(':');
            String namespace = separator < 0 ? "" : type.substring(0, separator + 1);
            String related = loadedFamilies.stream().filter(family -> family.startsWith(namespace))
                    .sorted().limit(32).collect(java.util.stream.Collectors.joining(", "));
            LOGGER.warn("Unable to map JEI recipe category {}; loaded families in namespace: [{}]",
                    type, related);
        }
    }

    public record Resolution(Set<ResourceLocation> jeiTypes, Set<String> families)
    {
        public Resolution
        {
            jeiTypes = Set.copyOf(jeiTypes);
            families = Set.copyOf(families);
        }

        public boolean isEmpty() { return families.isEmpty(); }
    }
}
