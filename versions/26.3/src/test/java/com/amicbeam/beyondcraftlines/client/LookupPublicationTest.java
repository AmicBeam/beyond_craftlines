package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class LookupPublicationTest
{
    @Test void backgroundCompletionCannotPublishIntoAnotherWorld()
    {
        ClientRecipeLookupIndex.clear();
        try
        {
            var recipe = new ClientRecipePlanner.Recipe(Identifier.parse("test:current"), "test:machine",
                    CacheTestKeys.key("output"), 1L, RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, List.of());
            var current = ClientRecipeLookupIndex.begin(new ClientRecipePlanner.Catalog(List.of(recipe)));
            var stale = ClientRecipeLookupIndex.begin(new ClientRecipePlanner.Catalog(List.of()));
            current.advance(Long.MAX_VALUE);
            assertFalse(ClientRecipeLookupIndex.ready());
            current.install();
            assertEquals(List.of("test:current"), ClientRecipeLookupIndex.recipeIds());
            stale.advance(Long.MAX_VALUE);
            assertTrue(stale.complete());
            assertEquals(List.of("test:current"), ClientRecipeLookupIndex.recipeIds());
        }
        finally { ClientRecipeLookupIndex.clear(); }
    }
}
