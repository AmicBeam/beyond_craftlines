package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeOutputResolverTest
{
    @Test
    void readsBothMekanismRotaryOutputDefinitions()
    {
        assertEquals(List.of("gas", "fluid"),
                RecipeOutputResolver.reflectiveOutputValues(new RotaryLikeRecipe()));
    }

    @Test
    void readsModernMekanismChemicalRotaryOutputDefinition()
    {
        assertEquals(List.of("chemical", "fluid"),
                RecipeOutputResolver.reflectiveOutputValues(new ModernRotaryLikeRecipe()));
    }

    @Test
    void unwrapsMekanismElectrolysisOutputRecord()
    {
        assertEquals(List.of("hydrogen", "oxygen"),
                RecipeOutputResolver.reflectiveOutputValues(new ElectrolysisLikeRecipe()));
    }

    @Test
    void unwrapsStackContainerOutput()
    {
        assertEquals(List.of("dirty_iron_slurry"),
                RecipeOutputResolver.reflectiveOutputValues(new BoxedChemicalLikeRecipe()));
    }

    @Test
    void readsGenericPublicOutputFields()
    {
        assertEquals(List.of("processed_item", "machine_result"),
                RecipeOutputResolver.reflectiveOutputValues(new PublicFieldOutputRecipe()));
    }

    @Test
    void readsMalumSpiritFocusingOutputAccessors()
    {
        assertEquals(List.of("prismarine_shard", "prismarine_shard_copy"),
                RecipeOutputResolver.reflectiveOutputValues(new MalumSpiritFocusingLikeRecipe()));
    }

    @Test
    void unwrapsCapabilityOutputMapsAndContentRecords()
    {
        assertEquals(List.of("assembled_machine"),
                RecipeOutputResolver.reflectiveOutputValues(new CapabilityMapRecipe()));
    }

    @Test
    void unwrapsCapabilityOutputMapsAndContentClasses()
    {
        assertEquals(List.of("assembled_machine"),
                RecipeOutputResolver.reflectiveOutputValues(new ClassCapabilityMapRecipe()));
    }

    @Test
    void rotaryChemicalOutputSelectsOnlyTheFluidInputDirection()
    {
        assertEquals(List.of("getFluidInput"), RecipeResourceResolver.directionalInputMethods(
                new ModernRotaryLikeRecipe(), "chemical"::equals));
    }

    @Test
    void rotaryFluidOutputSelectsOnlyTheChemicalInputDirection()
    {
        assertEquals(List.of("getGasInput", "getChemicalInput"),
                RecipeResourceResolver.directionalInputMethods(
                        new ModernRotaryLikeRecipe(), "fluid"::equals));
    }

    @Test
    void specializedRotaryDirectionSupportsModernAndLegacyChemicalKeys()
    {
        String rotary = "mekanism.api.recipes.RotaryRecipe";
        assertEquals(List.of("getFluidInput"), RecipeResourceResolver.mekanismRotaryInputMethods(
                rotary, "stack_type/chemical"));
        assertEquals(List.of("getFluidInput"), RecipeResourceResolver.mekanismRotaryInputMethods(
                rotary, "stack_type/chemicals/gas"));
        assertEquals(List.of("getGasInput", "getChemicalInput"),
                RecipeResourceResolver.mekanismRotaryInputMethods(rotary, "stack_type/fluid"));
        assertEquals(List.of(), RecipeResourceResolver.mekanismRotaryInputMethods(
                "example.OtherRecipe", "stack_type/chemical"));
    }

    @Test
    void chemicalInjectionAlwaysIncludesItsItemAndChemicalInputs()
    {
        assertEquals(List.of("getItemInput", "getChemicalInput"),
                RecipeResourceResolver.mekanismInputMethods(
                        "mekanism.api.recipes.ItemStackChemicalToObjectRecipe", "stack_type/item"));
        assertEquals(List.of("getItemInput", "getChemicalInput"),
                RecipeResourceResolver.mekanismInputMethods(
                        "mekanism.api.recipes.ItemStackGasToItemStackRecipe", "stack_type/item"));
    }

    private static final class RotaryLikeRecipe
    {
        public List<String> getGasOutputDefinition() { return List.of("gas"); }
        public List<String> getFluidOutputDefinition() { return List.of("fluid"); }
    }

    private static final class ModernRotaryLikeRecipe
    {
        public List<String> getChemicalOutputDefinition() { return List.of("chemical"); }
        public List<String> getFluidOutputDefinition() { return List.of("fluid"); }
    }

    private static final class ElectrolysisLikeRecipe
    {
        public List<SplitOutput> getOutputDefinition()
        { return List.of(new SplitOutput("hydrogen", "oxygen")); }
    }

    private static final class BoxedChemicalLikeRecipe
    {
        public List<BoxedStack> getOutputDefinition()
        { return List.of(new BoxedStack("dirty_iron_slurry")); }
    }

    private static final class PublicFieldOutputRecipe
    {
        public final String output = "processed_item";
        public final String result = "machine_result";
    }

    private static final class MalumSpiritFocusingLikeRecipe
    {
        public String getOutputRaw() { return "prismarine_shard"; }
        public String createOutput() { return "prismarine_shard_copy"; }
    }

    private static final class CapabilityMapRecipe
    {
        public final Map<String, List<CapabilityContent>> outputs = Map.of(
                "item", List.of(new CapabilityContent("assembled_machine", 10_000, 10_000)));
    }

    private static final class ClassCapabilityMapRecipe
    {
        public final Map<String, List<ClassCapabilityContent>> outputs = Map.of(
                "item", List.of(new ClassCapabilityContent("assembled_machine")));
    }

    public static final class ClassCapabilityContent
    {
        public final Object content;
        public final int chance = 10_000;
        public final int maxChance = 10_000;
        public ClassCapabilityContent(Object content) { this.content = content; }
    }

    private static final class BoxedStack
    {
        private final String stack;
        private BoxedStack(String stack) { this.stack = stack; }
        public String getChemicalStack() { return stack; }
    }

    private record SplitOutput(String left, String right) {}
    private record CapabilityContent(Object content, int chance, int maxChance) {}
}
