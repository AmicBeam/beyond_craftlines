package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeFurnaceRecipeFamiliesTest
{
    @Test
    void mapsVanillaJeiTypesToNativeExecutionFamilies()
    {
        assertEquals("smelting", NativeFurnaceRecipeFamilies.executionFamily("minecraft:furnace"));
        assertEquals("smelting", NativeFurnaceRecipeFamilies.executionFamily("minecraft:smelting"));
        assertEquals("blasting", NativeFurnaceRecipeFamilies.executionFamily("minecraft:blasting"));
        assertEquals("smoking", NativeFurnaceRecipeFamilies.executionFamily("minecraft:smoking"));
    }

    @Test
    void preservesNonNativeJeiTypes()
    {
        assertEquals("create:mixing", NativeFurnaceRecipeFamilies.executionFamily("create:mixing"));
    }

    @Test
    void requiresTheMatchingNativeFurnaceFamily()
    {
        Set<String> available = Set.of("smelting", "smoking", "create:mixing");
        assertTrue(NativeFurnaceRecipeFamilies.isAvailable("minecraft:furnace", available));
        assertTrue(NativeFurnaceRecipeFamilies.isAvailable("minecraft:smelting", available));
        assertTrue(NativeFurnaceRecipeFamilies.isAvailable("minecraft:smoking", available));
        assertFalse(NativeFurnaceRecipeFamilies.isAvailable("minecraft:blasting", available));
        assertFalse(NativeFurnaceRecipeFamilies.isAvailable("create:mixing", available));
    }
}
