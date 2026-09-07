package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MissingMaterialDisplayTest
{
    @Test
    void finalOutputCanNeverAppearAsAMissingIngredient()
    {
        LinkedHashMap<String, Long> missing = new LinkedHashMap<>();
        missing.put("healing", 1L);
        missing.put("nether_wart", 3L);

        assertEquals(Map.of("nether_wart", 3L),
                MissingMaterialDisplay.excludingFinalOutput(
                        missing, "healing", String::equals));
    }
}
