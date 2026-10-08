package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.runtime.OrderOutputDestination;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class PreferenceIdMigrationTest
{
    @Test void preservesRecipeAndIngredientDefaultsWithoutOverwritingAnExplicitNewChoice()
    {
        var oldId = Identifier.parse("test:old");
        var newId = Identifier.parse("test:new");
        var old = new ClientPlannerPreferences.Snapshot(Map.of("output", oldId),
                Map.of("test:old#0", "old_choice", "test:new#0", "explicit_choice", "test:old#1", "second"),
                OrderOutputDestination.NETWORK);
        var migrated = ClientPlannerPreferences.remapRecipeIds(old, Map.of(oldId, newId));
        assertEquals(Map.of("output", newId), migrated.recipes());
        assertEquals(Map.of("test:new#0", "explicit_choice", "test:new#1", "second"), migrated.ingredients());
        assertEquals(old.outputDestination(), migrated.outputDestination());
        assertSame(old, ClientPlannerPreferences.remapRecipeIds(old, Map.of()));
    }
}
