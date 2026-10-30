package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Actual component keys with a blade-state fixture; no shipped mod-name exception. */
final class WhiteSheathOutputPolicyTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }
    @Test void producedBladeStateIsBoundToItsOrderWithoutAProfile()
    {
        assertNull(getClass().getResource("/data/beyond_craftlines/recipe_io_profiles/slashblade.json"));
        var template = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("white_sheath_jei"));
        var actual = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("white_sheath_assembled"));
        var other = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("another_weapon"));
        var step = new RecipePlan.Step(DynamicCraftingFixtures.id("example:weapon"), "crafting", template, 1, 1, List.of(), List.of());
        var job = RecipeOrderJob.create(UUID.randomUUID(), UUID.randomUUID(), 1, DynamicCraftingFixtures.id("minecraft:iron_sword"),
                1, List.of(step), false, RecipeOrderJob.Status.QUEUED, "", 1, 0, List.of()).activate(0);
        assertFalse(job.matchesProduced(template, actual));
        job = job.recordProduced(actual, 1);
        assertTrue(job.matchesProduced(template, actual));
        assertFalse(job.matchesProduced(template, other));
        assertFalse(job.producedSelectionMatcher().test(IngredientSelectionKey.exact(template), actual));
        job = job.withReserved(List.of(new RecipePlan.ReservedMaterial(actual, 1)));
        assertTrue(job.producedSelectionMatcher().test(IngredientSelectionKey.exact(template), actual));
        assertFalse(job.producedSelectionMatcher().test(IngredientSelectionKey.exact(template), other));
        assertFalse(job.producedSelectionMatcher().test(IngredientSelectionKey.exact(other), actual));
        assertTrue(job.hasChangedFinalOutput());
        assertTrue(job.matchesFinalReceipt(actual));
        assertFalse(job.matchesFinalReceipt(other));
        assertFalse(RecipeIoProfileRegistry.outputMatches("example:weapon", template, actual));
        job = job.completeCrafts(1, 20).deactivate();
        assertTrue(job.matchesProduced(template, actual));
        assertEquals(1, job.executions().get(0).produced().get(0).amount());
    }
}
