package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VanillaProvisionerRecipeTypesTest
{
    @BeforeEach
    void installBundledJeiOnlyTypes()
    {
        JeiOnlyRecipeTypeRegistry.setServerRecipeValidationEnabled(true);
        JeiOnlyRecipeTypeRegistry.applySyncedTypes(Set.of(
                "minecraft:anvil", "minecraft:brewing", "minecraft:compostable"));
    }

    @AfterEach
    void restoreValidationMode()
    { JeiOnlyRecipeTypeRegistry.setServerRecipeValidationEnabled(true); }

    @Test
    void acceptsVanillaJeiCategoriesWithoutServerRecipeTypesForProvisionerBinding()
    {
        Set<String> requested = Set.of(
                "minecraft:anvil", "minecraft:brewing", "minecraft:compostable",
                "minecraft:smithing", "minecraft:stonecutting");

        assertTrue(VanillaProvisionerRecipeTypes.acceptsAll(requested, Set.of()));
        assertEquals(requested, VanillaProvisionerRecipeTypes.accepted(requested, Set.of()));
        assertEquals(Set.of("minecraft:smithing", "minecraft:stonecutting"),
                VanillaProvisionerRecipeTypes.executable(requested));
    }

    @Test
    void keepsAllFiveVanillaWorkstationsOffTheDirectMachinePath()
    {
        assertTrue(VanillaProvisionerRecipeTypes.isProvisionerOnly("minecraft:anvil"));
        assertTrue(VanillaProvisionerRecipeTypes.isProvisionerOnly("minecraft:brewing"));
        assertTrue(VanillaProvisionerRecipeTypes.isProvisionerOnly("minecraft:compostable"));
        assertTrue(VanillaProvisionerRecipeTypes.isProvisionerOnly("minecraft:smithing"));
        assertTrue(VanillaProvisionerRecipeTypes.isProvisionerOnly("minecraft:stonecutting"));
        assertFalse(VanillaProvisionerRecipeTypes.isJeiOnly("minecraft:smithing"));
        assertFalse(VanillaProvisionerRecipeTypes.isJeiOnly("minecraft:stonecutting"));
        assertEquals(Set.of("minecraft:smithing", "minecraft:stonecutting", "example:pressing"),
                VanillaProvisionerRecipeTypes.executable(Set.of(
                "minecraft:smithing", "minecraft:stonecutting", "example:pressing")));
    }

    @Test
    void identifiesJeiOnlyCategoriesWithoutServerRecipeHolders()
    {
        assertTrue(VanillaProvisionerRecipeTypes.isJeiOnly("minecraft:brewing"));
        assertTrue(VanillaProvisionerRecipeTypes.isJeiOnly("minecraft:anvil"));
        assertTrue(VanillaProvisionerRecipeTypes.isJeiOnly("minecraft:compostable"));
        assertEquals(Set.of("minecraft:brewing", "minecraft:anvil", "minecraft:compostable",
                        "minecraft:smithing", "stonecutting"),
                VanillaProvisionerRecipeTypes.provisionerFamilies(Set.of(
                        "minecraft:brewing", "minecraft:anvil", "minecraft:compostable",
                        "minecraft:smithing", "minecraft:stonecutting"), Set.of()));
    }

    @Test
    void mapsEveryVanillaWorkstationBlockToItsJeiCategory()
    {
        assertEquals("minecraft:brewing",
                VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:brewing_stand"));
        assertEquals("minecraft:smithing",
                VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:smithing_table"));
        assertEquals("minecraft:compostable",
                VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:composter"));
        assertEquals("minecraft:stonecutting",
                VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:stonecutter"));
        assertEquals("minecraft:anvil",
                VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:anvil"));
        assertEquals("minecraft:anvil",
                VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:chipped_anvil"));
        assertEquals("minecraft:anvil",
                VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:damaged_anvil"));
    }

    @Test
    void rejectsUnknownUnmappedCategories()
    {
        assertFalse(VanillaProvisionerRecipeTypes.acceptsAll(
                Set.of("example:virtual_machine"), Set.of()));
    }

    @Test
    void disablingServerValidationMakesEveryCategoryJeiOnly()
    {
        JeiOnlyRecipeTypeRegistry.setServerRecipeValidationEnabled(false);

        assertTrue(VanillaProvisionerRecipeTypes.isJeiOnly("example:machine"));
        assertEquals(Set.of(), VanillaProvisionerRecipeTypes.executable(Set.of("example:machine")));
        assertTrue(VanillaProvisionerRecipeTypes.acceptsAll(Set.of("example:machine"), Set.of()));
        assertEquals(Set.of("example:machine"), VanillaProvisionerRecipeTypes.directBindable(
                Set.of("example:machine", "minecraft:anvil")));
        assertEquals(Set.of("example:machine"), VanillaProvisionerRecipeTypes.directFamiliesForType(
                "example:machine", Set.of("example:server_family")));
    }

    @Test
    void datapackJeiOnlyTypesCanStillBindCapabilityBackedMachinesDirectly()
    {
        JeiOnlyRecipeTypeRegistry.applySyncedTypes(Set.of(
                "minecraft:anvil", "minecraft:brewing", "minecraft:compostable", "example:machine"));

        assertTrue(VanillaProvisionerRecipeTypes.isJeiOnly("example:machine"));
        assertFalse(VanillaProvisionerRecipeTypes.isProvisionerOnly("example:machine"));
        assertEquals(Set.of("example:machine"),
                VanillaProvisionerRecipeTypes.directBindable(Set.of("example:machine")));
        assertEquals(Set.of("example:machine"), VanillaProvisionerRecipeTypes.directFamiliesForType(
                "example:machine", Set.of("example:server_family")));
    }

    @Test
    void strictDirectBindingUsesMappedFamilyOrFallsBackToJeiUid()
    {
        assertEquals(Set.of("example:server_family"), VanillaProvisionerRecipeTypes.directFamiliesForType(
                "example:machine", Set.of("example:server_family")));
        assertEquals(Set.of("example:unknown"), VanillaProvisionerRecipeTypes.directFamiliesForType(
                "example:unknown", Set.of()));
    }
}
