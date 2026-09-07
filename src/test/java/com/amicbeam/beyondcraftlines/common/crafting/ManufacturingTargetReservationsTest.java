package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ManufacturingTargetReservationsTest
{
    @Test
    void keepsWaterBottleInputWhenManufacturingAwkwardPotion()
    {
        LinkedHashMap<String, Long> reserved = new LinkedHashMap<>();
        reserved.put("minecraft:potion|water", 3L);
        reserved.put("minecraft:nether_wart", 1L);
        reserved.put("minecraft:potion|awkward", 3L);

        ManufacturingTargetReservations.removeFinalOutput(reserved,
                "minecraft:potion|awkward", false, String::equals);

        assertEquals(Map.of("minecraft:potion|water", 3L, "minecraft:nether_wart", 1L), reserved);
    }
}
