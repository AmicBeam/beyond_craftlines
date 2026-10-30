package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class ComponentRecipeCandidatesTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }
    @Test void missingComponentsRequireAChoiceAndAnExplicitRecipeThenPlansNormally()
    {
        var requested = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("requested"));
        var a = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("a"));
        var b = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("b"));
        var material = DynamicCraftingFixtures.key(DynamicCraftingFixtures.potion(false, false));
        var slots = List.of(new ClientRecipePlanner.Slot(0, List.of(new ClientRecipePlanner.Candidate(material, 1)), VirtualInputUse.CONSUMED));
        var first = new ClientRecipePlanner.Recipe(DynamicCraftingFixtures.id("test:first"), "crafting", a, 1, RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, slots);
        var second = new ClientRecipePlanner.Recipe(DynamicCraftingFixtures.id("test:second"), "crafting", b, 1, RecipeIoProfileRegistry.OutputMatchSemantics.EXACT, slots);
        var catalog = new ClientRecipePlanner.Catalog(List.of(first, second));
        var stock = Map.<IStackKey<?>, Long>of(material, 1L);
        var none = ClientRecipePlanner.plan(catalog, stock, requested, 1, Map.of(), Map.of(), Map.of(), Map.of(), 8, 64, 1_000_000_000L, true);
        assertFalse(none.craftable(), "Similarity cannot automatically choose a component variant");
        var chosen = ClientRecipePlanner.plan(catalog, stock, requested, 1,
                Map.of(RecipeResourceResolver.resolutionKey(requested), second.id()), Map.of(), Map.of(), Map.of(), 8, 64, 1_000_000_000L, true);
        assertTrue(chosen.craftable(), chosen.toString());
        assertEquals(second.id(), chosen.recipes().get(RecipeResourceResolver.resolutionKey(requested)));
        assertEquals(stock, chosen.extraction());
        var exact = ClientRecipePlanner.plan(catalog, stock, a, 1, Map.of(), Map.of(), Map.of(), Map.of(), 8, 64, 1_000_000_000L, true);
        assertTrue(exact.craftable());
        assertEquals(first.id(), exact.recipes().get(RecipeResourceResolver.resolutionKey(a)));
        assertEquals(stock, exact.extraction());
    }
    @Test void sameItemDiscoveryDoesNotMakeInventoryKeysEqualOrGuessAmbiguousOutputs()
    {
        var a = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("a"));
        var b = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("b"));
        var wanted = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("wanted"));
        assertTrue(ComponentRecipeCandidates.sameResource(a, b));
        assertFalse(StackKeyMatch.exact(a, b));
        assertNull(ComponentRecipeCandidates.selectedOutput(wanted, List.of(new KeyAmount(a, 1), new KeyAmount(b, 1))));
        assertEquals(a, ComponentRecipeCandidates.selectedOutput(a, List.of(new KeyAmount(a, 1), new KeyAmount(b, 1))).key());
        assertNull(ComponentRecipeCandidates.selectedOutput(a, List.of(new KeyAmount(DynamicCraftingFixtures.key(DynamicCraftingFixtures.potion(false, false)), 1))));
        assertTrue(ComponentRecipeCandidates.distance(Map.of("id","iron","state","x"), Map.of("id","iron"))
                < ComponentRecipeCandidates.distance(Map.of("id","iron","state","x"), Map.of("id","gold")));
    }
}
