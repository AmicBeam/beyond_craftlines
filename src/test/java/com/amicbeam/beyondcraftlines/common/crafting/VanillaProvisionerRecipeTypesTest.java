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

    @Test void keepsFiveVanillaWorkstationsProvisionerOnly()
    {
        Set<String> types = Set.of("minecraft:anvil", "minecraft:brewing", "minecraft:compostable",
                "minecraft:smithing", "minecraft:stonecutting");
        types.forEach(type -> assertTrue(VanillaProvisionerRecipeTypes.isProvisionerOnly(type)));
        assertEquals(Set.of("example:machine"), VanillaProvisionerRecipeTypes.directBindable(
                Set.of("example:machine", "minecraft:anvil")));
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
