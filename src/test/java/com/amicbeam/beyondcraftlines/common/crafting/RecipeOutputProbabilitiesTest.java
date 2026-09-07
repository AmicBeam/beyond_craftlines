package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeOutputProbabilitiesTest
{
    @Test void neverPromisesExpectedRandomYield()
    {
        assertTrue(RecipeOutputProbabilities.guaranteed(1, 1));
        assertFalse(RecipeOutputProbabilities.guaranteed(0.2, 1));
        assertFalse(RecipeOutputProbabilities.guaranteed(2000, 10000));
        assertThrows(IllegalArgumentException.class, () -> RecipeOutputProbabilities.guaranteed(Double.NaN, 1));
    }

    @Test void shipsSourceBackedCreateProbabilityAccessors() throws Exception
    {
        try (var input = getClass().getResourceAsStream("/data/beyond_craftlines/recipe_io_profiles/create_probability.json"))
        {
            assertNotNull(input);
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var profile = RecipeIoProfileRegistry.parse(json);
            assertEquals("getRollableResults", profile.outputProbabilityRules().get(0).outputs());
            assertEquals("getStack", profile.outputProbabilityRules().get(0).stack());
            assertEquals("getChance", profile.outputProbabilityRules().get(0).chance());
            assertEquals(1, profile.outputProbabilityRules().get(0).scale());
        }
    }
    @Test void occultismRitualIconsAreExplicitlyExcludedFromProduction() throws Exception
    {
        try (var input = getClass().getResourceAsStream("/data/beyond_craftlines/recipe_io_profiles/occultism_presentation.json"))
        {
            assertNotNull(input);
            var profile = RecipeIoProfileRegistry.parse(JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
            assertEquals(java.util.Set.of("getRitualDummy"), profile.ignoredOutputFields());
            assertEquals(java.util.Set.of("com.klikli_dev.occultism.crafting.recipe.RitualRecipe"), profile.recipeClasses());
        }
    }

}
