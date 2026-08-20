package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Maps JEI category ids to the server recipe families that Craftlines can plan.
 * The mapping is deliberately mod-agnostic: namespaced JEI category ids must
 * equal loaded server RecipeType ids; vanilla ids use Craftlines' short names.
 */
public final class JeiRecipeFamilyRegistry
{
    private JeiRecipeFamilyRegistry() {}

    public static Resolution resolve(Set<ResourceLocation> jeiTypes, Set<String> loadedFamilies)
    {
        var raw = JeiRecipeFamilyMappings.resolve(
                jeiTypes.stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.toSet()),
                loadedFamilies);
        Set<ResourceLocation> acceptedTypes = raw.jeiTypes().stream()
                .map(ResourceLocation::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Resolution(acceptedTypes, raw.families());
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
