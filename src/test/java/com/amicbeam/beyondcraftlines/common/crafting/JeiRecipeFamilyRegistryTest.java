package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeiRecipeFamilyRegistryTest
{
    private static final Map<String, Set<String>> ALIASES = Map.ofEntries(
            Map.entry("minecraft:furnace", Set.of("smelting")),
            Map.entry("create:packing", Set.of("create:compacting")),
            Map.entry("mekanism:enrichment_chamber", Set.of("mekanism:enriching")),
            Map.entry("mekanism:metallurgic_infuser", Set.of("mekanism:metallurgic_infusing")),
            Map.entry("mekanism:condensentrating", Set.of("mekanism:rotary")),
            Map.entry("mekanism:decondensentrating", Set.of("mekanism:rotary")),
            Map.entry("ars_nouveau:enchantment_apparatus", Set.of("ars_nouveau:enchantment")),
            Map.entry("actuallyadditions:empowerer", Set.of("actuallyadditions:empower")));

    private static JeiRecipeFamilyMappings.Resolution resolve(Set<String> types, Set<String> loaded)
    { return JeiRecipeFamilyMappings.resolve(types, loaded, ALIASES, Map.of()); }

    @Test
    void sameIdNamespacedCategoryMapsToLoadedServerRecipeType()
    {
        var result = resolve(
                Set.of("example:pressing"), Set.of("example:pressing"));
        assertEquals(Set.of("example:pressing"), result.jeiTypes());
        assertEquals(Set.of("example:pressing"), result.families());
    }

    @Test
    void vanillaNamespaceMapsToCraftlinesShortFamily()
    {
        var result = resolve(
                Set.of("minecraft:smelting"), Set.of("smelting"));
        assertEquals(Set.of("smelting"), result.families());
    }

    @Test
    void jeiVanillaFurnaceAliasMapsToSmelting()
    {
        var result = resolve(
                Set.of("minecraft:furnace"), Set.of("smelting"));
        assertEquals(Set.of("smelting"), result.families());
    }

    @Test
    void createBasinPackingCategoryMapsToCompactingRecipeType()
    {
        var result = resolve(
                Set.of("create:mixing", "create:packing", "create:basin"),
                Set.of("create:mixing", "create:compacting"));
        assertEquals(Set.of("create:mixing", "create:packing"), result.jeiTypes());
        assertEquals(Set.of("create:mixing", "create:compacting"), result.families());
    }

    @Test
    void createPackingCategoryIsRejectedWithoutCompactingRecipeType()
    {
        assertTrue(resolve(
                Set.of("create:packing"), Set.of("create:mixing")).isEmpty());
    }

    @Test
    void mekanismMachineCategoriesMapToServerProcessTypes()
    {
        var result = resolve(
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
        var result = resolve(
                Set.of("mekanism:condensentrating", "mekanism:decondensentrating"),
                Set.of("mekanism:rotary"));
        assertEquals(Set.of("mekanism:condensentrating", "mekanism:decondensentrating"), result.jeiTypes());
        assertEquals(Set.of("mekanism:rotary"), result.families());
    }

    @Test
    void arsNouveauEnchantmentCategoryMapsToTheServerRecipeType()
    {
        var result = resolve(
                Set.of("ars_nouveau:enchantment_apparatus"),
                Set.of("ars_nouveau:enchantment"));
        assertEquals(Set.of("ars_nouveau:enchantment_apparatus"), result.jeiTypes());
        assertEquals(Set.of("ars_nouveau:enchantment"), result.families());
    }

    @Test
    void actuallyAdditionsEmpowererCategoryMapsToEmpoweringRecipeType()
    {
        var result = resolve(
                Set.of("actuallyadditions:empowerer"),
                Set.of("actuallyadditions:empower"));
        assertEquals(Set.of("actuallyadditions:empowerer"), result.jeiTypes());
        assertEquals(Set.of("actuallyadditions:empower"), result.families());
    }

    @Test
    void categoryIsRejectedWhenServerRecipeTypeIsNotLoaded()
    {
        assertTrue(resolve(
                Set.of("example:pressing"), Set.of()).isEmpty());
    }

    @Test
    void differentlyNamedCategoryIsNotGuessed()
    {
        assertTrue(resolve(
                Set.of("example:press"), Set.of("example:pressing")).isEmpty());
    }

    @Test
    void manualSelectionShowsMappedAndCompatibilityCategoriesWithoutDebug()
    {
        Set<String> categories = Set.of("minecraft:anvil", "minecraft:smelting", "create:packing");
        Set<String> loaded = Set.of("smelting", "create:compacting");

        assertEquals(categories,
                ManualRecipeTypeVisibility.visible(categories, loaded, ALIASES, Map.of(), false));
        assertEquals(categories,
                ManualRecipeTypeVisibility.visible(categories, loaded, ALIASES, Map.of(), true));
        assertTrue(ManualRecipeTypeVisibility.usesCompatibilityMode(
                "minecraft:anvil", loaded, ALIASES, Map.of()));
        assertFalse(ManualRecipeTypeVisibility.usesCompatibilityMode(
                "create:packing", loaded, ALIASES, Map.of()));
    }

    @Test
    void manualSelectionFallsBackToJeiCategoriesWhenSyncedMappingIsEmpty()
    {
        Set<String> categories = Set.of("minecraft:crafting", "example:machine");

        assertEquals(categories, ManualRecipeTypeVisibility.visibleOrAllWhenUnresolved(
                categories, Set.of(), Map.of(), Map.of(), false));
    }

    @Test
    void manualSelectionKeepsUnmappedCategoriesWhenAnotherCategoryCanBeMapped()
    {
        Set<String> categories = Set.of("minecraft:crafting", "example:virtual");

        assertEquals(categories,
                ManualRecipeTypeVisibility.visibleOrAllWhenUnresolved(
                        categories, Set.of("crafting"), Map.of(), Map.of(), false));
    }

    @Test
    void verifiedRecipeSampleCanSupplyAPreviouslyUnknownMapping()
    {
        var result = JeiRecipeFamilyMappings.resolve(Set.of("example:press"),
                Set.of("example:pressing"), Map.of(),
                Map.of("example:press", Set.of("example:pressing")));
        assertEquals(Set.of("example:press"), result.jeiTypes());
        assertEquals(Set.of("example:pressing"), result.families());
    }

    @Test
    void exactIdTakesPriorityOverHintsAndAliases()
    {
        var result = JeiRecipeFamilyMappings.resolve(Set.of("example:press"),
                Set.of("example:press", "example:pressing", "example:compressing"),
                Map.of("example:press", Set.of("example:compressing")),
                Map.of("example:press", Set.of("example:pressing")));
        assertEquals(Set.of("example:press"), result.families());
    }

    @Test
    void hintsHaveABoundedRoundTripEncoding()
    {
        var hint = new RecipeFamilyHint("example:press", "example:pressing", "example:copper_plate");
        assertEquals(hint, RecipeFamilyHint.decode(hint.encode()));
        assertEquals(null, RecipeFamilyHint.decode("invalid"));
    }
}
