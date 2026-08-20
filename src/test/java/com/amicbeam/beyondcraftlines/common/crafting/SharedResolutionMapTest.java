package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SharedResolutionMapTest
{
    @Test
    void keepsOneResolutionForEachCanonicalKey()
    {
        SharedResolutionMap<String, String> resolutions = new SharedResolutionMap<>();
        resolutions.put("iron_ingot", "blast_iron", "duplicate");

        assertEquals("blast_iron", resolutions.get("iron_ingot"));
        assertEquals("blast_iron", resolutions.copy().get("iron_ingot"));
    }

    @Test
    void rejectsASecondResolutionForTheSameCanonicalKey()
    {
        SharedResolutionMap<String, String> resolutions = new SharedResolutionMap<>();
        resolutions.put("iron_ingot", "blast_iron", "duplicate product");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolutions.put("iron_ingot", "smelt_iron", "duplicate product"));
        assertEquals("duplicate product", error.getMessage());
    }
}
