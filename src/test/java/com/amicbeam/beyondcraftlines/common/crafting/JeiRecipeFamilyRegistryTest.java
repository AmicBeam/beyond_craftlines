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
    void createBasinPackingCategoryMapsToCompactingRecipeType()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("create:mixing", "create:packing", "create:basin"),
                Set.of("create:mixing", "create:compacting"));
        assertEquals(Set.of("create:mixing", "create:packing"), result.jeiTypes());
        assertEquals(Set.of("create:mixing", "create:compacting"), result.families());
    }

    @Test
    void createPackingCategoryIsRejectedWithoutCompactingRecipeType()
    {
        assertTrue(JeiRecipeFamilyMappings.resolve(
                Set.of("create:packing"), Set.of("create:mixing")).isEmpty());
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
    void arsNouveauEnchantmentCategoryMapsToTheServerRecipeType()
    {
        var result = JeiRecipeFamilyMappings.resolve(
                Set.of("ars_nouveau:enchantment_apparatus"),
                Set.of("ars_nouveau:enchantment"));
        assertEquals(Set.of("ars_nouveau:enchantment_apparatus"), result.jeiTypes());
        assertEquals(Set.of("ars_nouveau:enchantment"), result.families());
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
