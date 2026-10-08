package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class IncrementalPlanningCatalogTest
{
    @BeforeEach void before() { VirtualProvisionerRecipeRegistry.clear(); CacheTestKeys.register(); }
    @AfterEach void after() { VirtualProvisionerRecipeRegistry.clear(); }

    @Test void reusesExistingRecordsCapturesOnlyAddedIdAndDropsDeletedId()
    {
        var unchanged = holder("unchanged");
        var removed = holder("removed");
        var added = holder("added");
        var retained = captured(unchanged);
        var cached = new ClientRecipePlanner.Catalog(List.of(retained, captured(removed)));
        var builder = ClientRecipePlanner.beginCapture(null, List.of(unchanged, added), cached);
        assertEquals(1, builder.reusedRecipes());
        assertEquals(1, builder.missingRecipes());
        assertEquals(1, builder.removedRecipes());
        while (!builder.complete()) builder.advance(1_000_000L);
        assertEquals(2, builder.catalog().recipes().size());
        assertSame(retained, builder.catalog().recipes().stream().filter(r -> r.id().equals(retained.id())).findFirst().orElseThrow());
        assertTrue(builder.catalog().recipes().stream().anyMatch(r -> r.id().equals(added.id().identifier())));
        assertFalse(builder.catalog().recipes().stream().anyMatch(r -> r.id().equals(removed.id().identifier())));
    }

    @Test void deletionOnlyNeedsNoRecipeCaptureAndPreservesMultipleOutputDirections()
    {
        var unchanged = holder("unchanged");
        var removed = holder("removed");
        var first = captured(unchanged);
        var second = new ClientRecipePlanner.Recipe(first.id(), first.family(), CacheTestKeys.key("second_output"),
                2L, first.outputMatch(), first.slots());
        var builder = ClientRecipePlanner.beginCapture(null, List.of(unchanged),
                new ClientRecipePlanner.Catalog(List.of(first, second, captured(removed))));
        assertTrue(builder.complete());
        assertEquals(0, builder.captureSteps());
        assertEquals(List.of(first, second), builder.catalog().recipes());
    }

    private static RecipeHolder<?> holder(String output)
    {
        return VirtualProvisionerRecipeRegistry.register(new VirtualProvisionerRecipeRegistry.Descriptor(
                "test:machine", CacheTestKeys.key(output), 1L,
                List.of(new VirtualProvisionerRecipeRegistry.InputSlot("input",
                        List.of(new KeyAmount(CacheTestKeys.key("raw"), 1L))))));
    }
    private static ClientRecipePlanner.Recipe captured(RecipeHolder<?> holder)
    {
        var descriptor = VirtualProvisionerRecipeRegistry.descriptor(holder.value());
        return new ClientRecipePlanner.Recipe(holder.id().identifier(), descriptor.family(), descriptor.output(),
                descriptor.outputAmount(), RecipeIoProfileRegistry.OutputMatchSemantics.EXACT,
                List.of(new ClientRecipePlanner.Slot(0,
                        List.of(new ClientRecipePlanner.Candidate(CacheTestKeys.key("raw"), 1L)), VirtualInputUse.CONSUMED)));
    }
}
