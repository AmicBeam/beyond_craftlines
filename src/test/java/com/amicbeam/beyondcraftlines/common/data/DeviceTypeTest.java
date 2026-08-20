package com.amicbeam.beyondcraftlines.common.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DeviceTypeTest
{
    @Test
    void rejectsTheThreeNativeBeyondMachines()
    {
        assertTrue(DeviceType.fromBlockId("beyonddimensions:net_furnace_block").isNativeBeyondRecipeMachine());
        assertTrue(DeviceType.fromBlockId("beyonddimensions:net_blast_furnace_block").isNativeBeyondRecipeMachine());
        assertTrue(DeviceType.fromBlockId("beyonddimensions:net_smoker_block").isNativeBeyondRecipeMachine());
    }

    @Test
    void acceptsOnlyThirdPartyNamespaces()
    {
        assertTrue(DeviceType.isThirdPartyMachine("example:processing_machine"));
        assertFalse(DeviceType.isThirdPartyMachine("minecraft:furnace"));
        assertFalse(DeviceType.isThirdPartyMachine("beyonddimensions:net_furnace_block"));
        assertFalse(DeviceType.isThirdPartyMachine("beyond_craftlines:schematic_executor"));
    }
}
