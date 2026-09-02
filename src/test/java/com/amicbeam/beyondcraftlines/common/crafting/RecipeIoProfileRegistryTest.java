package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeIoProfileRegistryTest
{
    @Test void shipsSlashBladeDynamicOutputPolicy()
    {
        RecipeIoProfileRegistry.Profile profile = read("slashblade.json");

        assertEquals(Set.of("mods.flammpfeil.slashblade.recipe.SlashBladeShapedRecipe"),
                profile.recipeClasses());
        assertEquals(RecipeIoProfileRegistry.OutputMatchSemantics.SAME_RESOURCE,
                profile.outputMatch());
        assertTrue(profile.outputFields().isEmpty(),
                "getOutputBlade is an identifier, not the assembled ItemStack");
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

    private record Blade(String item, String state)
    {
        private boolean sameItem(Blade other) { return item.equals(other.item); }
        private boolean sameComponents(Blade other)
        { return sameItem(other) && state.equals(other.state); }
    }
}
