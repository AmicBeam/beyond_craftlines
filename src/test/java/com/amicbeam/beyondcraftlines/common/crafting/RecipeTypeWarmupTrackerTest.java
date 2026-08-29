package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeTypeWarmupTrackerTest
{
    @Test void requestsEachTypeOnlyOnceUntilCleared()
    {
        RecipeTypeWarmupTracker<String> tracker = new RecipeTypeWarmupTracker<>();

        assertEquals(List.of("example:crusher", "example:mixer"),
                tracker.activate(List.of("example:crusher", "example:mixer")));
        assertEquals(List.of(), tracker.activate(List.of("example:crusher")));
        assertEquals(Set.of("example:crusher", "example:mixer"), tracker.activeTypes());
        assertFalse(tracker.ready(Set.of("example:crusher")));

        tracker.complete("example:crusher");
        assertTrue(tracker.ready(Set.of("example:crusher")));
        assertFalse(tracker.ready(Set.of("example:crusher", "example:mixer")));

        tracker.clear();
        assertEquals(List.of("example:crusher"), tracker.activate(List.of("example:crusher")));
    }
}
