package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JeiOnlyRecipeTypeRegistryTest
{
    @AfterEach
    void restoreDefaults()
    {
        JeiOnlyRecipeTypeRegistry.setServerRecipeValidationEnabled(true);
        JeiOnlyRecipeTypeRegistry.applySyncedTypes(Set.of(
                "minecraft:anvil", "minecraft:brewing", "minecraft:compostable"));
    }

    @Test
    void bundledVanillaTypesComeFromDatapackJson() throws Exception
    {
        try (var stream = getClass().getResourceAsStream(
                "/data/beyond_craftlines/jei_only_recipe_types/vanilla.json"))
        {
            var parsed = JeiOnlyRecipeTypeRegistry.parse(JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject());
            assertEquals(Set.of("minecraft:anvil", "minecraft:brewing", "minecraft:compostable"), parsed);
        }
    }

    @Test
    void supportsSingleAndMultipleIdsWhileIgnoringInvalidValues()
    {
        assertEquals(Set.of("example:single"), JeiOnlyRecipeTypeRegistry.parse(
                JsonParser.parseString("{\"jei_type\":\"example:single\"}").getAsJsonObject()));
        assertEquals(Set.of("example:one", "example:two"), JeiOnlyRecipeTypeRegistry.parse(
                JsonParser.parseString("""
                        {"jei_types":["example:one","Example:invalid","example:two",7]}
                        """).getAsJsonObject()));
    }

    @Test
    void synchronizedTypesAreValidated()
    {
        JeiOnlyRecipeTypeRegistry.applySyncedTypes(Set.of(
                "example:z", "invalid", "example:a"));

        assertEquals(Set.of("example:z", "example:a"), JeiOnlyRecipeTypeRegistry.recipeTypes());
    }
}
