package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class RootDiagnosticRulesTest
{
    @Test void bindingLookupMatchesExactCategoriesAndOnlyKnownVanillaAliases()
    {
        assertTrue(RootDiagnosticRules.matchesBinding("test:altar", Set.of("test:altar"), Set.of()));
        assertTrue(RootDiagnosticRules.matchesBinding("test:altar", Set.of(), Set.of("test:altar")));
        assertFalse(RootDiagnosticRules.matchesBinding("test:altar", Set.of("test:press"), Set.of("test:press")));
        assertTrue(RootDiagnosticRules.matchesBinding("minecraft:furnace", Set.of("smelting"), Set.of()));
        assertTrue(RootDiagnosticRules.matchesBinding("minecraft:crafting", Set.of("crafting"), Set.of()));
        assertFalse(RootDiagnosticRules.matchesBinding("test:furnace", Set.of("smelting"), Set.of()));
    }

    @Test void playerCategoryExpectationDistinguishesMissingDisabledPendingAndEmpty()
    {
        assertEquals("expected_category_unknown", RootDiagnosticRules.expectedCategoryStatus(true, false, true, 0));
        assertEquals("expected_category_disabled", RootDiagnosticRules.expectedCategoryStatus(true, true, false, 3));
        assertEquals("pending", RootDiagnosticRules.expectedCategoryStatus(false, true, true, 0));
        assertEquals("expected_category_empty", RootDiagnosticRules.expectedCategoryStatus(true, true, true, 0));
        assertEquals("expected_category_found", RootDiagnosticRules.expectedCategoryStatus(true, true, true, 2));
    }

    @Test void nativeFallbackAndViewerCategoriesAreNotMissingCategories()
    {
        assertEquals(java.util.List.of("mod:altar"), RootDiagnosticRules.missingCategories(
                Set.of("mod:altar", "crafting", "minecraft:stonecutting", "mod:native", "mod:viewer"),
                Set.of("mod:viewer"), Set.of("mod:native")));
    }

    @Test void unfinishedIndexCannotProveMissingOrWrongRecipe()
    {
        assertEquals("pending", RootDiagnosticRules.outputStatus(false, false, 0, false));
        assertEquals("pending", RootDiagnosticRules.outputStatus(false, true, 0, false));
        assertEquals("no_recipe", RootDiagnosticRules.outputStatus(true, false, 0, false));
    }

    @Test void exactVariantsWinAndAmbiguousVariantsMustNotBeGuessed()
    {
        assertEquals("exact", RootDiagnosticRules.outputStatus(true, true, 3, true));
        assertEquals("ambiguous", RootDiagnosticRules.outputStatus(true, true, 2, false));
        assertEquals("components", RootDiagnosticRules.outputStatus(true, true, 1, false));
        assertEquals("wrong_output", RootDiagnosticRules.outputStatus(true, true, 0, false));
    }

    @Test void differencesIncludeMissingAndChangedFieldsInStableBoundedOrder()
    {
        Map<String, String> target = new LinkedHashMap<>();
        Map<String, String> output = new LinkedHashMap<>();
        target.put("/a", "old"); output.put("/a", "new");
        target.put("/b", "target-only"); output.put("/c", "output-only");
        var differences = RootDiagnosticRules.differences(target, output);
        assertEquals(3, differences.size());
        assertEquals("/a: target=old; output=new", differences.getFirst());
        assertEquals("/b: target=target-only; output=<absent>", differences.get(1));
        assertEquals("/c: target=<absent>; output=output-only", differences.get(2));
        for (int i = 0; i < 100; i++) target.put("/z" + i, "x");
        assertEquals(16, RootDiagnosticRules.differences(target, output).size());
        assertTrue(RootDiagnosticRules.differences(output, output).isEmpty());
    }
}
