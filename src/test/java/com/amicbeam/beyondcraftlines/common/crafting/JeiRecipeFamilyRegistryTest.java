package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeiRecipeFamilyRegistryTest
{
    @Test
    void sameIdNamespacedCategoryMapsToLoadedServerRecipeType()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("example:pressing"), Set.of("example:pressing"));
        assertEquals(Set.of("example:pressing"), result.jeiTypes());
        assertEquals(Set.of("example:pressing"), result.families());
    }

    @Test
    void vanillaNamespaceMapsToCraftlinesShortFamily()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("minecraft:smelting"), Set.of("smelting"));
        assertEquals(Set.of("smelting"), result.families());
    }

    @Test
    void categoryIsRejectedWhenServerRecipeTypeIsNotLoaded()
    {
        assertTrue(JeiRecipeFamilyMappings.resolve(
                Set.of("example:pressing"), Set.of()).isEmpty());
    }

    @Test
    void differentlyNamedCategoryIsNotGuessed()
    {
        assertTrue(JeiRecipeFamilyMappings.resolve(
                Set.of("example:press"), Set.of("example:pressing")).isEmpty());
    }
}
