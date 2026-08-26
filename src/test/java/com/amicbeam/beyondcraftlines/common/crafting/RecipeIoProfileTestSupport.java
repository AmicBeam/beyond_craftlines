package com.amicbeam.beyondcraftlines.common.crafting;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

final class RecipeIoProfileTestSupport
{
    private RecipeIoProfileTestSupport() {}

    static void install(String... names)
    {
        ArrayList<String> encoded = new ArrayList<>();
        for (String name : names)
        {
            try (var stream = RecipeIoProfileTestSupport.class.getResourceAsStream(
                    "/data/beyond_craftlines/recipe_io_profiles/" + name))
            {
                var reader = new InputStreamReader(java.util.Objects.requireNonNull(stream),
                        StandardCharsets.UTF_8);
                encoded.add(com.google.gson.JsonParser.parseReader(reader).toString());
            }
            catch (java.io.IOException exception)
            { throw new java.io.UncheckedIOException(exception); }
        }
        RecipeIoProfileRegistry.applyEntriesForTests(encoded);
    }
}
