package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    private static final class BoxedStack
    {
        private final String stack;
        private BoxedStack(String stack) { this.stack = stack; }
        public String getChemicalStack() { return stack; }
    }

    private record SplitOutput(String left, String right) {}
}
