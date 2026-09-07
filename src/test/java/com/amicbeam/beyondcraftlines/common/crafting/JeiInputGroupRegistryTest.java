package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JeiInputGroupRegistryTest
{
    @AfterEach void clear() { JeiInputGroupRegistry.clear(); }

    @Test void remembersDraconicFusionGroupsBeforeAnyVirtualRecipeUpload()
    {
        JeiInputGroupRegistry.rememberEncoded(java.util.List.of(
                "draconicevolution:fusion_crafting|catalyst",
                "draconicevolution:fusion_crafting|ingredients"));

        assertEquals(Set.of("catalyst", "ingredients"),
                JeiInputGroupRegistry.groups("draconicevolution:fusion_crafting"));
    }

    @Test void roundTripsBoundedCategoryGroupHints()
    {
        var encoded = JeiInputGroupRegistry.encode(Map.of(
                "mekanism:condensentrating", Set.of("chemical_input"),
                "mekanism:decondensentrating", Set.of("fluid_input")));
        JeiInputGroupRegistry.rememberEncoded(encoded);

        assertEquals(Set.of("chemical_input"), JeiInputGroupRegistry.groups("mekanism:condensentrating"));
        assertEquals(Set.of("fluid_input"), JeiInputGroupRegistry.groups("mekanism:decondensentrating"));
    }
}
