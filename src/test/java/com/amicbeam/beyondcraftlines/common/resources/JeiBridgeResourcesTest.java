package com.amicbeam.beyondcraftlines.common.resources;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

final class JeiBridgeResourcesTest
{
    @Test void everyVersionShipsTheJeiLayoutBridgeConfiguration() throws Exception
    {
        try (var input = getClass().getResourceAsStream("/beyond_craftlines.mixins.json"))
        {
            assertNotNull(input, "The JEI relation bridge must not be excluded along with optional EMI support");
            var config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.getAsJsonArray("client").asList().stream()
                    .anyMatch(value -> value.getAsString().equals("jei.RecipeLayoutRelationsMixin")));
            assertNotNull(getClass().getResource("/com/amicbeam/beyondcraftlines/mixin/jei/RecipeLayoutRelationsMixin.class"));
        }
    }
}
