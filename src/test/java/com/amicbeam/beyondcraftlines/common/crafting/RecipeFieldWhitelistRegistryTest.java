package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RecipeFieldWhitelistRegistryTest
{
    @Test
    void parsesRecipeTypeScopedFieldsAndStrictDefaultPolicy()
    {
        var parsed = RecipeFieldWhitelistRegistry.parse(JsonParser.parseString("""
                {
                  "recipe_types": ["example:pressing", "example:compressing"],
                  "include_defaults": false,
                  "input_fields": ["input", "activationItem", "invalid-name()"],
                  "output_fields": ["result"]
                }
                """).getAsJsonObject());

        assertEquals(Set.of("example:pressing", "example:compressing"), parsed.keySet());
        assertFalse(parsed.get("example:pressing").includeDefaults());
        assertEquals(Set.of("input", "activationItem"), parsed.get("example:pressing").inputFields());
        assertEquals(Set.of("result"), parsed.get("example:pressing").outputFields());
    }

    @Test
    void defaultsRemainEnabledWhenNotExplicitlyDisabled()
    {
        var parsed = RecipeFieldWhitelistRegistry.parse(JsonParser.parseString("""
                {
                  "recipe_type": "example:pressing",
                  "input_fields": ["input"],
                  "output_fields": ["output"]
                }
                """).getAsJsonObject());

        assertEquals(Set.of("input"), parsed.get("example:pressing").inputFields());
        assertEquals(Set.of("output"), parsed.get("example:pressing").outputFields());
        assertEquals(true, parsed.get("example:pressing").includeDefaults());
    }
}
