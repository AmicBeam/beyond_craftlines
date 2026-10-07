package com.amicbeam.beyondcraftlines.common.crafting;

import aztech.modern_industrialization.machines.recipe.MachineRecipe;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class NativeRecipeFallbackPolicyTest
{
    private static final String FAMILY = "modern_industrialization:star_altar";

    private static String profile() throws Exception
    {
        try (var stream = NativeRecipeFallbackPolicyTest.class.getResourceAsStream(
                "/data/beyond_craftlines/recipe_io_profiles/modern_industrialization_star_altar.json"))
        {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void declaredNativeFamilyExistsEvenWhenJeiHasNoCategory() throws Exception
    {
        try
        {
            RecipeIoProfileRegistry.applyEntriesForTests(List.of(profile()));
            assertEquals(Set.of(FAMILY), RecipeIoProfileRegistry.nativeFallbackFamilies());
            assertTrue(RecipeCatalogScope.fullFamilies(Set.of(), RecipeIoProfileRegistry.nativeFallbackFamilies())
                    .contains(FAMILY));
            assertTrue(RecipeIoProfileRegistry.allowsNativeFallback(new MachineRecipe(), FAMILY));
            assertFalse(RecipeIoProfileRegistry.allowsNativeFallback(new MachineRecipe(), null));
            assertFalse(RecipeIoProfileRegistry.allowsNativeFallback(null, FAMILY));
            assertFalse(RecipeIoProfileRegistry.allowsNativeFallback(new MachineRecipe(), "modern_industrialization:assembler"));
            assertFalse(RecipeIoProfileRegistry.allowsNativeFallback(new Object(), FAMILY));
        }
        finally { RecipeIoProfileRegistry.applyEntriesForTests(List.of()); }
    }

    @Test void chanceInputsOutputsFluidsAndMultipleOutputsCannotUseTheNativePath() throws Exception
    {
        var policy = RecipeIoProfileRegistry.parse(JsonParser.parseString(profile()).getAsJsonObject()).nativeFallback();
        MachineRecipe recipe = new MachineRecipe();
        assertTrue(policy.matches(recipe));
        recipe.itemInputs = List.of(new MachineRecipe.Input(1, 0.5f));
        assertFalse(policy.matches(recipe));
        recipe.itemInputs = List.of(new MachineRecipe.Input(1, 0));
        assertFalse(policy.matches(recipe));
        recipe.itemInputs = List.of(new MachineRecipe.Input(1, 1));
        recipe.itemOutputs = List.of(new MachineRecipe.Output(1, Float.NaN));
        assertFalse(policy.matches(recipe));
        recipe.itemOutputs = List.of(new MachineRecipe.Output(1, 1), new MachineRecipe.Output(1, 1));
        assertFalse(policy.matches(recipe));
        recipe.itemOutputs = List.of(new MachineRecipe.Output(1, 1));
        recipe.fluidInputs = List.of(new Object());
        assertFalse(policy.matches(recipe));
        recipe.fluidInputs = List.of();
        recipe.fluidOutputs = List.of(new Object());
        assertFalse(policy.matches(recipe));
        recipe.fluidOutputs = List.of();
        recipe.itemInputs = List.of();
        assertFalse(policy.matches(recipe));
    }

    @Test void nativeFallbackStillRequiresNetworkFamilyAvailability()
    {
        assertTrue(com.amicbeam.beyondcraftlines.common.menu.RecipeIndexVisibility
                .includesPlanningRecipe(FAMILY, false, true, Set.of(FAMILY)));
        assertFalse(com.amicbeam.beyondcraftlines.common.menu.RecipeIndexVisibility
                .includesPlanningRecipe(FAMILY, false, true, Set.of()));
        assertFalse(com.amicbeam.beyondcraftlines.common.menu.RecipeIndexVisibility
                .includesPlanningRecipe(FAMILY, false, false, Set.of(FAMILY)));
        assertFalse(NativeRecipeFallbackPolicy.parse(null).matches(new MachineRecipe()));
    }
}
