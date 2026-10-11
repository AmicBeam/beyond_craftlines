package com.amicbeam.beyondcraftlines.common.data;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class ResourceFilterTest
{
    private static boolean accepts(boolean whitelist, List<String> slots, String key)
    { return ResourceFilterRules.accepts(whitelist, slots, key, String::isEmpty, String::equals); }

    @Test void emptyBlacklistAllowsAndEmptyWhitelistBlocksResources()
    {
        assertTrue(accepts(false, List.of(), "item:iron|normal"));
        assertFalse(accepts(true, List.of(), "item:iron|normal"));
        assertFalse(accepts(false, List.of(), null));
        assertFalse(accepts(false, List.of(), ""));
    }

    @Test void modesMatchResourceTypeAndComponents()
    {
        var water = List.of("fluid:water|normal");
        assertTrue(accepts(true, water, "fluid:water|normal"));
        assertFalse(accepts(true, water, "item:water_bucket|normal"));
        assertFalse(accepts(true, water, "fluid:water|custom"));
        assertFalse(accepts(false, water, "fluid:water|normal"));
        assertTrue(accepts(false, water, "chemical:oxygen|normal"));
    }

    @Test void emptyMarkersDoNotMatchOrChangeModeSemantics()
    {
        var slots = ResourceFilterRules.normalize(List.of("chemical:oxygen|normal"), "");
        assertEquals(36, slots.size());
        assertTrue(accepts(true, slots, "chemical:oxygen|normal"));
        assertFalse(accepts(false, slots, "chemical:oxygen|normal"));
        assertFalse(accepts(true, ResourceFilterRules.normalize(List.of(), ""), "chemical:oxygen|normal"));
    }

    @Test void nbtMatchingIsOptionalAndDamageIsNotAnIndependentCondition()
    {
        assertTrue(ResourceFilterRules.matchesItemMetadata(false, false));
        assertFalse(ResourceFilterRules.matchesItemMetadata(false, true));
        assertTrue(ResourceFilterRules.matchesItemMetadata(true, true));
    }

    @Test void recipeModeChecksTheSelectedSideWithoutRequiringBothSidesInWhitelist()
    {
        var onlyProduct = List.of("item:steel");
        java.util.function.Predicate<String> productWhitelist = key -> accepts(true, onlyProduct, key);
        assertTrue(ResourceFilterRules.acceptsRecipeInput(true, "item:iron", "item:steel", productWhitelist));
        assertFalse(ResourceFilterRules.acceptsRecipeInput(true, "item:iron", "item:copper", productWhitelist));
        assertFalse(ResourceFilterRules.acceptsRecipeInput(false, "item:iron", "item:steel", productWhitelist));
        assertTrue(ResourceFilterRules.acceptsRecipeInput(false, "item:steel", "item:copper", productWhitelist));
        java.util.function.Predicate<String> productBlacklist = key -> accepts(false, onlyProduct, key);
        assertFalse(ResourceFilterRules.acceptsRecipeInput(true, "item:iron", "item:steel", productBlacklist));
        assertTrue(ResourceFilterRules.acceptsRecipeInput(true, "item:steel", "item:copper", productBlacklist));
    }

    @Test void slotSnapshotIsImmutableAndRejectsOversizeLists()
    {
        var source = new ArrayList<String>();
        source.add("chemical:oxygen|normal");
        source.add(null);
        var slots = ResourceFilterRules.normalize(source, "");
        source.clear();
        assertEquals("chemical:oxygen|normal", slots.get(0));
        assertEquals("", slots.get(1));
        assertThrows(UnsupportedOperationException.class, () -> slots.set(0, ""));
        assertThrows(IllegalArgumentException.class, () -> ResourceFilterRules.normalize(
                java.util.Collections.nCopies(37, "item:iron|normal"), ""));
    }
}
