package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class VanillaProvisionerRecipeTypesTest
{
    @Test void everyCategoryUsesItsJeiUidAsTheOnlyRuntimeFamily()
    {
        assertTrue(VanillaProvisionerRecipeTypes.isJeiOnly("example:machine"));
        assertEquals(Set.of(), VanillaProvisionerRecipeTypes.executable(Set.of("example:machine")));
        assertTrue(VanillaProvisionerRecipeTypes.acceptsAll(Set.of("example:machine"), Set.of()));
        assertEquals(Set.of("example:machine"), VanillaProvisionerRecipeTypes.directFamiliesForType(
                "example:machine", Set.of("example:server_family")));
        assertEquals(Set.of("example:machine"), VanillaProvisionerRecipeTypes.provisionerFamilies(
                Set.of("example:machine"), Set.of("example:server_family")));
    }

    @Test void brewingIsDirectlyBindableWhileOtherManualWorkstationsRemainProvisionerOnly()
    {
        Set<String> types = Set.of("minecraft:anvil", "minecraft:compostable",
                "minecraft:smithing", "minecraft:stonecutting");
        types.forEach(type -> assertTrue(VanillaProvisionerRecipeTypes.isProvisionerOnly(type)));
        assertFalse(VanillaProvisionerRecipeTypes.isProvisionerOnly("minecraft:brewing"));
        assertEquals(Set.of("example:machine", "minecraft:brewing"),
                VanillaProvisionerRecipeTypes.directBindable(
                        Set.of("example:machine", "minecraft:anvil", "minecraft:brewing")));
    }

    @Test void smithingAndStonecuttingExecuteInsideTheNetwork()
    {
        assertEquals(Set.of("minecraft:smithing", "minecraft:stonecutting"),
                VanillaProvisionerRecipeTypes.executable(Set.of(
                        "minecraft:smithing", "minecraft:stonecutting", "minecraft:brewing")));
        assertTrue(VanillaProvisionerRecipeTypes.isNetworkExecutable("minecraft:smithing", true));
        assertFalse(VanillaProvisionerRecipeTypes.isNetworkExecutable("minecraft:smithing", false));
        assertFalse(VanillaProvisionerRecipeTypes.isNetworkExecutable("minecraft:brewing", true));
    }

    @Test void mapsEveryVanillaWorkstationBlockToItsJeiCategory()
    {
        assertEquals("minecraft:brewing", VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:brewing_stand"));
        assertEquals("minecraft:smithing", VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:smithing_table"));
        assertEquals("minecraft:compostable", VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:composter"));
        assertEquals("minecraft:stonecutting", VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:stonecutter"));
        assertEquals("minecraft:anvil", VanillaProvisionerRecipeTypes.categoryForBlock("minecraft:anvil"));
    }
}
