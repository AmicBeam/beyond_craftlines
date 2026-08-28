package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeFamilyAliasRegistryTest
{
    @Test
    void shipsTouhouLittleMaidAltarAliasesForLegacyAndModernRecipeTypes()
    {
        Map<String, Set<String>> aliases = read("touhou_little_maid.json");
        assertEquals(Set.of(
                        "touhou_little_maid:altar_crafting",
                        "touhou_little_maid:altar_recipe"),
                aliases.get("touhou_little_maid:altar"));

        assertEquals(Set.of("touhou_little_maid:altar_crafting"),
                resolve(aliases, Set.of("touhou_little_maid:altar_crafting")));
        assertEquals(Set.of("touhou_little_maid:altar_recipe"),
                resolve(aliases, Set.of("touhou_little_maid:altar_recipe")));
    }

    private static Set<String> resolve(Map<String, Set<String>> aliases, Set<String> loadedFamilies)
    {
        return JeiRecipeFamilyMappings.resolve(
                Set.of("touhou_little_maid:altar"), loadedFamilies, aliases, Map.of()).families();
    }

    private static Map<String, Set<String>> read(String name)
    {
        try (var stream = RecipeFamilyAliasRegistryTest.class.getResourceAsStream(
                "/data/beyond_craftlines/recipe_type_aliases/" + name))
        {
            var object = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> families = new LinkedHashSet<>();
            object.getAsJsonArray("recipe_types").forEach(value -> families.add(value.getAsString()));
            return Map.of(object.get("jei_type").getAsString(), Set.copyOf(families));
        }
        catch (java.io.IOException exception)
        { throw new java.io.UncheckedIOException(exception); }
    }
}
