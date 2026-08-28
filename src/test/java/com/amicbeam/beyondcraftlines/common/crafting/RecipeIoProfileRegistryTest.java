package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RecipeIoProfileRegistryTest
{
    @Test
    void shipsArsNouveauEnchantingApparatusInputs()
    {
        var profile = read("ars_nouveau.json");
        assertEquals(Set.of("ars_nouveau:enchanting_apparatus"), profile.recipeTypes());
        assertEquals(Set.of("reagent", "pedestalItems"), profile.inputFields());
        assertEquals(Set.of("result"), profile.outputFields());
    }

    @Test
    void shipsActuallyAdditionsEmpowererStandInputs()
    {
        var profile = read("actually_additions.json");
        assertEquals(Set.of("actuallyadditions:empower"), profile.recipeTypes());
        assertEquals(Set.of("de.ellpeck.actuallyadditions.mod.crafting.EmpowererRecipe"),
                profile.recipeClasses());
        assertEquals(Set.of("getInput", "getStandOne", "getStandTwo", "getStandThree", "getStandFour"),
                profile.inputFields());
        assertEquals(Set.of("getOutput"), profile.outputFields());
    }

    @Test
    void shipsImmersiveEngineeringCokeOvenBatchAndCreosoteMappings()
    {
        var profile = read("immersiveengineering_coke_oven.json");
        assertEquals(RecipeIoProfileRegistry.InputCountSemantics.BATCH_LIMIT,
                profile.inputCountSemantics().get("input"));
        assertEquals(java.util.List.of(new RecipeIoProfileRegistry.OutputMapping(
                        RecipeIoProfileRegistry.OutputType.FLUID,
                        "immersiveengineering:creosote", "creosoteOutput")),
                profile.outputMappings());
    }

    @Test
    void shipsAllGenericStructuralVocabularyInTheDefaultProfile()
    {
        var profile = read("defaults.json");
        assertEquals(true, profile.inputFields().containsAll(Set.of(
                "getFluidIngredients", "catalyst", "getCatalyst", "activationItem", "getActivationItem",
                "spirits", "getSpirits")));
        assertEquals(Set.of("catalyst", "getCatalyst"), profile.distinctInputFields());
        assertEquals(false, profile.inputFields().stream()
                .anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("energy")));
        assertEquals(Set.of("content", "getContent"), profile.structuralWrapperFields());
        assertEquals(Set.of("getChemicalStack"), profile.outputWrapperFields());
    }

    @Test
    void shipsMekanismDirectionsAndMultipliersAsData()
    {
        var profile = read("mekanism.json");
        assertEquals(3, profile.directions().size());
        assertEquals(2, profile.multipliers().size());
        assertEquals(200, profile.multipliers().get(0).factor());
        assertEquals("perTickUsage", profile.multipliers().get(0).whenBooleanField());
    }

    @Test
    void shipsMekanismSawingMainAndSecondaryOutputs()
    {
        var profile = read("mekanism_sawing.json");
        assertEquals(Set.of("mekanism:sawing"), profile.recipeTypes());
        assertEquals(Set.of("getMainOutputDefinition", "getSecondaryOutputDefinition"),
                profile.outputFields());
    }

    @Test
    void parsesScopedFieldsRulesAndRejectsInvalidNames()
    {
        var profile = RecipeIoProfileRegistry.parse(JsonParser.parseString("""
                {
                  "recipe_types": ["example:pressing", "example:compressing"],
                  "include_defaults": false,
                  "input_fields": ["input", "activationItem", "invalid-name()"],
                  "distinct_input_fields": ["input", "notAnInput"],
                  "output_fields": ["result"],
                  "input_count_semantics": {
                    "input": "batch_limit",
                    "invalid-name()": "batch_limit",
                    "activationItem": "unknown"
                  },
                  "output_mappings": [
                    {"type": "fluid", "id": "example:oil", "amount_field": "oilAmount"},
                    {"type": "unknown", "id": "example:bad", "amount_field": "amount"},
                    {"type": "item", "id": "INVALID", "amount_field": "amount"}
                  ]
                }
                """).getAsJsonObject());

        assertEquals(Set.of("example:pressing", "example:compressing"), profile.recipeTypes());
        assertFalse(profile.includeDefaults());
        assertEquals(Set.of("input", "activationItem"), profile.inputFields());
        assertEquals(Set.of("input"), profile.distinctInputFields());
        assertEquals(Set.of("result"), profile.outputFields());
        assertEquals(java.util.Map.of("input", RecipeIoProfileRegistry.InputCountSemantics.BATCH_LIMIT),
                profile.inputCountSemantics());
        assertEquals(java.util.List.of(new RecipeIoProfileRegistry.OutputMapping(
                        RecipeIoProfileRegistry.OutputType.FLUID, "example:oil", "oilAmount")),
                profile.outputMappings());
    }

    private static RecipeIoProfileRegistry.Profile read(String name)
    {
        try (var stream = RecipeIoProfileRegistryTest.class.getResourceAsStream(
                "/data/beyond_craftlines/recipe_io_profiles/" + name))
        {
            return RecipeIoProfileRegistry.parse(JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject());
        }
        catch (java.io.IOException exception)
        { throw new java.io.UncheckedIOException(exception); }
    }
}
