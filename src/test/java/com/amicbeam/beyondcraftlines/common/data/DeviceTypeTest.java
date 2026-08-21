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
    void acceptsVanillaAndThirdPartyMachinesButRejectsNetworkComponents()
    {
        assertTrue(DeviceType.isBindableMachine("minecraft:furnace"));
        assertTrue(DeviceType.isBindableMachine("minecraft:brewing_stand"));
        assertTrue(DeviceType.isBindableMachine("example:processing_machine"));
        assertFalse(DeviceType.isBindableMachine("beyonddimensions:net_furnace_block"));
        assertFalse(DeviceType.isBindableMachine("beyonddimensions:net_hopper_block"));
        assertFalse(DeviceType.isBindableMachine("beyond_craftlines:craftline_provisioner"));
        assertFalse(DeviceType.isBindableMachine("invalid"));
    }
}
