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
}
