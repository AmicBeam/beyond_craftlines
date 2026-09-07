package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RootRecipeOverridePolicyTest
{
    @Test void manualRootChoiceOutranksJeiEntryRecipe()
    {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("minecraft:iron_ingot", "minecraft:iron_ingot_from_smelting_iron_ore");

        RootRecipeOverridePolicy.putInitialFallback(overrides, "minecraft:iron_ingot",
                "minecraft:iron_ingot_from_iron_block");

        assertEquals("minecraft:iron_ingot_from_smelting_iron_ore",
                overrides.get("minecraft:iron_ingot"));
    }

    @Test void jeiEntryRecipeRemainsTheAutomaticRootFallback()
    {
        Map<String, String> overrides = new LinkedHashMap<>();

        RootRecipeOverridePolicy.putInitialFallback(overrides, "minecraft:iron_ingot",
                "minecraft:iron_ingot_from_iron_block");

        assertEquals("minecraft:iron_ingot_from_iron_block", overrides.get("minecraft:iron_ingot"));
    }

    @Test void targetOnlyEntryDoesNotPinAnArbitraryRootRecipe()
    {
        Map<String, String> overrides = new LinkedHashMap<>();

        RootRecipeOverridePolicy.putInitialFallback(overrides, "minecraft:iron_ingot", null);

        assertFalse(overrides.containsKey("minecraft:iron_ingot"));
    }
}
