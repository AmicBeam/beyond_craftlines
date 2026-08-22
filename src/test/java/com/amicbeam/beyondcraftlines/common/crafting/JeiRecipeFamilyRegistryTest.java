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
    void jeiVanillaFurnaceAliasMapsToSmelting()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("minecraft:furnace"), Set.of("smelting"));
        assertEquals(Set.of("smelting"), result.families());
    }

    @Test
    void mekanismMachineCategoriesMapToServerProcessTypes()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("mekanism:enrichment_chamber", "mekanism:metallurgic_infuser"),
                Set.of("mekanism:enriching", "mekanism:metallurgic_infusing"));
        assertEquals(Set.of("mekanism:enrichment_chamber", "mekanism:metallurgic_infuser"),
                result.jeiTypes());
        assertEquals(Set.of("mekanism:enriching", "mekanism:metallurgic_infusing"),
                result.families());
    }

    @Test
    void mekanismRotaryDirectionsMapToTheSharedServerRecipeType()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("mekanism:condensentrating", "mekanism:decondensentrating"),
                Set.of("mekanism:rotary"));
        assertEquals(Set.of("mekanism:condensentrating", "mekanism:decondensentrating"), result.jeiTypes());
        assertEquals(Set.of("mekanism:rotary"), result.families());
    }

    @Test
    void arsNouveauLegacyCategoriesAndBlockFallbackMapToServerTypes()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("ars_nouveau:glyph_recipe", "ars_nouveau:enchantment_apparatus",
                        "ars_nouveau:imbuement_chamber"),
                Set.of("ars_nouveau:glyph", "ars_nouveau:enchantment", "ars_nouveau:imbuement"));
        assertEquals(Set.of("ars_nouveau:glyph", "ars_nouveau:enchantment", "ars_nouveau:imbuement"),
                result.families());
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
