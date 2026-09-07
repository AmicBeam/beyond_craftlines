package com.amicbeam.beyondcraftlines.common.resources;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ItemModelDefinitionTest
{
    @Test
    void everyRegisteredItemHasA26ModelDefinition() throws Exception
    {
        for (String item : new String[]{"network_linker", "craftline_provisioner", "craftline_dashboard"})
        {
            try (var stream = ItemModelDefinitionTest.class.getResourceAsStream(
                    "/assets/beyond_craftlines/items/" + item + ".json"))
            {
                assertNotNull(stream, "missing 26.1.2 item definition for " + item);
                var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("model");
                assertEquals("minecraft:model", model.get("type").getAsString());
                assertEquals("beyond_craftlines:item/" + item, model.get("model").getAsString());
            }
        }
    }
}
