package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeIoProfileRegistryTest
{
    @Test void shipsSlashBladeDynamicOutputPolicy()
    {
        RecipeIoProfileRegistry.Profile profile = read("slashblade.json");

        assertEquals(Set.of("slashblade:"), profile.recipeIdPrefixes());
        assertEquals(Set.of("slashblade"), profile.resourceNamespaces());
        assertTrue(profile.recipeClasses().isEmpty(),
                "the profile must cover SlashBlade_2 vanilla recipe classes and Resharped alike");
        assertEquals(RecipeIoProfileRegistry.OutputMatchSemantics.SAME_RESOURCE,
                profile.outputMatch());
        assertEquals(new RecipeIoProfileRegistry.DynamicOutputPolicy(
                "jei_focus", "exact", RecipeIoProfileRegistry.OutputMatchSemantics.SAME_RESOURCE,
                "assemble_selected_inputs"), profile.dynamicOutput());
        assertTrue(RecipeIoProfileRegistry.matchesRecipeId(
                "slashblade:anvilcrafting/reforge", profile.recipeIdPrefixes()));
        assertTrue(RecipeIoProfileRegistry.matchesRecipeId(
                "slashblade:rodai_netherite_smithing", profile.recipeIdPrefixes()));
        assertFalse(RecipeIoProfileRegistry.matchesRecipeId(
                "minecraft:diamond_sword", profile.recipeIdPrefixes()));
    }

    @Test void shipsImmersiveEngineeringCokeOvenBatchAndCreosoteMappings()
    {
        RecipeIoProfileRegistry.Profile profile = read("immersiveengineering_coke_oven.json");

        assertEquals(Set.of("immersiveengineering:coke_oven"), profile.recipeTypes());
        assertEquals(Set.of("input"), profile.inputFields());
        assertEquals(RecipeIoProfileRegistry.InputCountSemantics.BATCH_LIMIT,
                profile.inputCountSemantics().get("input"));
        assertEquals(Set.of("getMatchingStackList", "getMatchingStacks"),
                Set.copyOf(profile.representationFields()));
        assertEquals(List.of(new RecipeIoProfileRegistry.CountedWrapper(
                        Set.of("getBaseIngredient"), Set.of("getCount"))),
                profile.countedWrappers());
        assertEquals(List.of(new RecipeIoProfileRegistry.OutputMapping(
                        RecipeIoProfileRegistry.OutputType.FLUID,
                        "immersiveengineering:creosote", "creosoteOutput")),
                profile.outputMappings());
    }

    @Test void keepsUnconfiguredRecipesComponentExact()
    {
        Blade requested = new Blade("slashblade:slashblade", "awakened");
        Blade declared = new Blade("slashblade:slashblade", "base");

        assertFalse(RecipeIoProfileRegistry.outputMatches(
                RecipeIoProfileRegistry.OutputMatchSemantics.EXACT,
                requested, declared, Blade::sameComponents, Blade::sameItem));
        assertTrue(RecipeIoProfileRegistry.outputMatches(
                RecipeIoProfileRegistry.OutputMatchSemantics.SAME_RESOURCE,
                requested, declared, Blade::sameComponents, Blade::sameItem));
        assertFalse(RecipeIoProfileRegistry.outputMatches(
                RecipeIoProfileRegistry.OutputMatchSemantics.SAME_RESOURCE,
                requested, new Blade("slashblade:proudsoul", "base"),
                Blade::sameComponents, Blade::sameItem));
    }

    @Test void rejectsIncompleteDynamicOutputDeclarations()
    {
        var profile = RecipeIoProfileRegistry.parse(JsonParser.parseString("""
                {
                  "recipe_id_prefixes": ["example:"],
                  "dynamic_output": {
                    "source": "jei_focus",
                    "planning_fallback": "same_resource"
                  }
                }
                """).getAsJsonObject());

        assertEquals(RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, profile.outputMatch());
        assertTrue(profile.dynamicOutput().source().isBlank());
    }

    @Test void resolvesRuntimePolicyByRecipeIdWithoutARecipeClass()
    {
        try
        {
            RecipeIoProfileRegistry.applyEntriesForTests(List.of(readText("slashblade.json")));
            assertTrue(RecipeIoProfileRegistry.allowsSameResourceOutput(
                    "slashblade:anvilcrafting/reforge"));
            assertFalse(RecipeIoProfileRegistry.allowsSameResourceOutput(
                    "minecraft:diamond_sword"));
            assertTrue(RecipeIoProfileRegistry.allowsSameResourceMaterial("slashblade"));
            assertFalse(RecipeIoProfileRegistry.allowsSameResourceMaterial("minecraft"));
        }
        finally { RecipeIoProfileRegistry.applyEntriesForTests(List.of()); }
    }

    @Test void shipsMekanismInputTankConversionsForAllFiveVersions()
    {
        var chemical = read("mekanism_chemical_conversion.json");
        var gas = read("mekanism_gas_conversion.json");
        var infusion = read("mekanism_infusion_conversion.json");
        assertEquals(Set.of("mekanism:chemical_conversion"), chemical.recipeTypes());
        assertEquals(Set.of("mekanism:gas_conversion"), gas.recipeTypes());
        assertEquals(Set.of("mekanism:infusion_conversion"), infusion.recipeTypes());
        for (var profile : List.of(chemical, gas, infusion))
        {
            assertEquals(Set.of("getInput"), profile.inputFields());
            assertEquals(Set.of("getOutputDefinition"), profile.outputFields());
            assertEquals(Set.of("getRepresentations"), profile.representationFields());
            assertTrue(profile.inputConversion().active());
        }
        assertEquals(Set.of("mekanism:purifying", "mekanism:compressing", "mekanism:injecting",
                "mekanism:dissolution", "mekanism:nucleosynthesizing", "mekanism:metallurgic_infusing"),
                chemical.inputConversion().consumerRecipeTypes());
        assertTrue(gas.inputConversion().consumerRecipeTypes().containsAll(Set.of(
                "mekanism:purifying", "mekanism:purification_chamber", "mekanism:osmium_compressor",
                "mekanism:chemical_injection_chamber", "mekanism:chemical_dissolution_chamber",
                "mekanism:antiprotonic_nucleosynthesizer")));
        assertEquals(Set.of("mekanism:metallurgic_infuser", "mekanism:metallurgic_infusing"),
                infusion.inputConversion().consumerRecipeTypes());
    }

    @Test void onlyExplicitConversionProfilesAuthorizeBundlingAndSurviveJsonSync()
    {
        try
        {
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers(
                    "mekanism:chemical_conversion", "mekanism:conversion/flint").isEmpty());
            RecipeIoProfileRegistry.applyEntriesForTests(List.of(readText("mekanism_chemical_conversion.json"),
                    readText("mekanism_gas_conversion.json"), readText("mekanism_infusion_conversion.json")));
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers(
                    "mekanism:chemical_conversion", "beyond_craftlines:virtual/test").contains("mekanism:purifying"));
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers(
                    "mekanism:gas_conversion", "mekanism:conversion/flint").contains("mekanism:purification_chamber"));
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers(
                    "mekanism:oxidizing", "mekanism:oxidizing/iron").isEmpty());
            assertFalse(RecipeIoProfileRegistry.machineInputConversionConsumers(
                    "mekanism:chemical_conversion", "mekanism:conversion/flint").contains("mekanism:crushing"));
            var encoded = RecipeIoProfileRegistry.encodedEntries();
            RecipeIoProfileRegistry.applyEntriesForTests(List.of());
            RecipeIoProfileRegistry.applyEntriesForTests(encoded);
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers(
                    "mekanism:infusion_conversion", "test:carbon").contains("mekanism:metallurgic_infuser"));
        }
        finally { RecipeIoProfileRegistry.applyEntriesForTests(List.of()); }
    }

    @Test void rejectsMissingModeConsumerListAndUnverifiedSelectors()
    {
        for (String declaration : List.of("{}", "{\"mode\":\"other\"}",
                "{\"mode\":\"machine_input\"}",
                "{\"mode\":\"machine_input\",\"consumer_recipe_types\":[\"Invalid Type\"]}"))
            assertFalse(RecipeIoProfileRegistry.parseInputConversion(
                    JsonParser.parseString(declaration).getAsJsonObject()).active());
        try
        {
            RecipeIoProfileRegistry.applyEntriesForTests(List.of("""
                    {"input_conversion":{"mode":"machine_input","consumer_recipe_types":["test:consumer"]}}
                    """));
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers("test:producer", "test:recipe").isEmpty());
            RecipeIoProfileRegistry.applyEntriesForTests(List.of("""
                    {"recipe_type":"test:producer","recipe_id_prefixes":["test:allowed/"],
                    "input_conversion":{"mode":"machine_input","consumer_recipe_types":["test:consumer"]}}
                    """));
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers("test:producer", "test:other/recipe").isEmpty());
            assertEquals(Set.of("test:consumer"), RecipeIoProfileRegistry.machineInputConversionConsumers(
                    "test:producer", "test:allowed/recipe"));
            RecipeIoProfileRegistry.applyEntriesForTests(List.of("""
                    {"recipe_type":"test:producer","recipe_classes":["test.NarrowRecipe"],
                    "input_conversion":{"mode":"machine_input","consumer_recipe_types":["test:consumer"]}}
                    """));
            assertTrue(RecipeIoProfileRegistry.machineInputConversionConsumers("test:producer", "test:recipe").isEmpty());
        }
        finally { RecipeIoProfileRegistry.applyEntriesForTests(List.of()); }
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

    private static String readText(String name)
    {
        try (var stream = RecipeIoProfileRegistryTest.class.getResourceAsStream(
                "/data/beyond_craftlines/recipe_io_profiles/" + name))
        {
            return new String(java.util.Objects.requireNonNull(stream).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
        catch (java.io.IOException exception)
        { throw new java.io.UncheckedIOException(exception); }
    }

    private record Blade(String item, String state)
    {
        private boolean sameItem(Blade other) { return item.equals(other.item); }
        private boolean sameComponents(Blade other)
        { return sameItem(other) && state.equals(other.state); }
    }
}
