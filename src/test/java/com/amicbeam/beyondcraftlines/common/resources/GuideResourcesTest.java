package com.amicbeam.beyondcraftlines.common.resources;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GuideResourcesTest
{
    @Test
    void provisionerItemNameExistsInBothLanguages() throws Exception
    {
        assertEquals("Craftline Provisioner", language("en_us")
                .get("item.beyond_craftlines.craftline_provisioner").getAsString());
        assertEquals("合成链供给器", language("zh_cn")
                .get("item.beyond_craftlines.craftline_provisioner").getAsString());
    }

    @Test
    void dashboardGuideIsAssociatedWithItsItemInBothLanguages() throws Exception
    {
        assertDashboardAssociation(readText("/assets/beyond_craftlines/guides/"
                + "beyond_craftlines/guide/craftline_dashboard.md"));
        assertDashboardAssociation(readText("/assets/beyond_craftlines/guides/"
                + "beyond_craftlines/guide/_zh_cn/craftline_dashboard.md"));
    }

    private static com.google.gson.JsonObject language(String language) throws Exception
    {
        try (var stream = GuideResourcesTest.class.getResourceAsStream(
                "/assets/beyond_craftlines/lang/" + language + ".json"))
        {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static String readText(String path) throws Exception
    {
        try (var stream = GuideResourcesTest.class.getResourceAsStream(path))
        {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertDashboardAssociation(String guide)
    {
        assertTrue(guide.startsWith("---\n"));
        assertTrue(guide.substring(0, guide.indexOf("\n---", 4))
                .contains("item_ids:\n  - beyond_craftlines:craftline_dashboard"));
    }
}
