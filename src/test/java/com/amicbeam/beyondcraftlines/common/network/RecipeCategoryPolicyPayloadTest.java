package com.amicbeam.beyondcraftlines.common.network;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeCategoryPolicyPayloadTest
{
    @Test void roundTripsProfilesAndExactCategoryPolicyTogether()
    {
        var entries = new ArrayList<>(List.of("{\"recipe_classes\":[]}"));
        var blocked = new ArrayList<>(List.of("create:automatic_shaped", "create:automatic_shapeless"));
        var original = new RecipeIoProfilePayload(entries, blocked);
        entries.clear(); blocked.clear();
        var buffer = Unpooled.buffer();
        try
        {
            RecipeIoProfilePayload.STREAM_CODEC.encode(buffer, original);
            assertEquals(original, RecipeIoProfilePayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
        finally { buffer.release(); }
    }

    @Test void emptyPolicySurvivesTransportWithoutReinstallingDefaults()
    {
        var buffer = Unpooled.buffer();
        try
        {
            var payload = new RecipeIoProfilePayload(List.of(), List.of());
            RecipeIoProfilePayload.STREAM_CODEC.encode(buffer, payload);
            assertTrue(RecipeIoProfilePayload.STREAM_CODEC.decode(buffer).categoryBlacklist().isEmpty());
        }
        finally { buffer.release(); }
    }
}
