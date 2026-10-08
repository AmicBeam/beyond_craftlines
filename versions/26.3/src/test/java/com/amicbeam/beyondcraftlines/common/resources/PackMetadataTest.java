package com.amicbeam.beyondcraftlines.common.resources;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class PackMetadataTest
{
    @Test
    void usesMinecraft26_3DataAndResourcePackRange() throws Exception
    {
        try (var stream = PackMetadataTest.class.getResourceAsStream("/pack.mcmeta"))
        {
            assertNotNull(stream);
            var pack = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("pack");

            assertFalse(pack.has("pack_format"));
            assertFalse(pack.has("supported_formats"));
            assertEquals(97, pack.getAsJsonArray("min_format").get(0).getAsInt());
            assertEquals(1, pack.getAsJsonArray("min_format").get(1).getAsInt());
            assertEquals(121, pack.getAsJsonArray("max_format").get(0).getAsInt());
            assertEquals(0, pack.getAsJsonArray("max_format").get(1).getAsInt());

            var client = PackMetadataSection.CLIENT_TYPE.codec().parse(JsonOps.INSTANCE, pack).getOrThrow();
            var server = PackMetadataSection.SERVER_TYPE.codec().parse(JsonOps.INSTANCE, pack).getOrThrow();
            assertEquals(PackFormat.of(97, 1), client.supportedFormats().minInclusive());
            assertEquals(PackFormat.of(121, 0), server.supportedFormats().maxInclusive());
        }
    }
}
