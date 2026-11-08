package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.runtime.OrderOutputDestination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class ApproximatePreferencePersistenceTest
{
    @TempDir Path directory;

    @Test void hiddenCandidatesAndOutputDestinationRoundTripTogether() throws Exception
    {
        Path path = directory.resolve("preferences.json");
        var original = new ClientPlannerPreferences.Snapshot(Map.of("target", id("test:recipe")),
                Map.of("test:recipe#0", "test:ingredient"), OrderOutputDestination.INVENTORY, true);
        assertTrue(ClientPlannerPreferences.writeTo(path, original));
        assertEquals(original, ClientPlannerPreferences.read(path));
        assertFalse(Files.exists(directory.resolve("preferences.json.tmp")));
        var shown = new ClientPlannerPreferences.Snapshot(original.recipes(), original.ingredients(),
                original.outputDestination(), false);
        assertTrue(ClientPlannerPreferences.writeTo(path, shown));
        assertEquals(shown, ClientPlannerPreferences.read(path));
    }

    @Test void existingPreferenceFilesDefaultToShowingAllCandidates() throws Exception
    {
        Path path = directory.resolve("old.json");
        Files.writeString(path, "{\"version\":1,\"output_destination\":\"inventory\",\"recipes\":{},\"ingredients\":{}}");
        var read = ClientPlannerPreferences.read(path);
        assertFalse(read.hideApproximate());
        assertEquals(OrderOutputDestination.INVENTORY, read.outputDestination());
    }

    @Test void recipeIdMigrationPreservesTheVisibilityAndDestinationChoices()
    {
        var original = new ClientPlannerPreferences.Snapshot(Map.of("target", id("test:old")),
                Map.of("test:old#0", "ingredient"), OrderOutputDestination.INVENTORY, true);
        var migrated = ClientPlannerPreferences.remapRecipeIds(original,
                Map.of(original.recipes().get("target"), id("test:new")));
        assertTrue(migrated.hideApproximate());
        assertEquals(OrderOutputDestination.INVENTORY, migrated.outputDestination());
        assertEquals("test:new", migrated.recipes().get("target").toString());
        assertEquals(Map.of("test:new#0", "ingredient"), migrated.ingredients());
    }

    @SuppressWarnings("unchecked") private static <T> T id(String path)
    {
        for (String name : new String[]{"net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier"})
        {
            try { return (T) Class.forName(name).getMethod("tryParse", String.class).invoke(null, path); }
            catch (ClassNotFoundException ignored) {}
            catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }
        throw new IllegalStateException("No resource ID type");
    }
}
