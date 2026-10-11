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

    @Test void nbtAndDurabilityMatchingAreIndependent()
    {
        assertTrue(ResourceFilterRules.matchesItemMetadata(false, true, false, false, false));
        assertFalse(ResourceFilterRules.matchesItemMetadata(false, true, true, true, false));
        assertFalse(ResourceFilterRules.matchesItemMetadata(true, true, false, false, true));
        assertTrue(ResourceFilterRules.matchesItemMetadata(true, true, false, true, false));
        assertTrue(ResourceFilterRules.matchesItemMetadata(false, true, true, false, true));
        assertFalse(ResourceFilterRules.matchesItemMetadata(true, true, false, true, true));
        assertTrue(ResourceFilterRules.matchesItemMetadata(true, false, false, true, true));
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
