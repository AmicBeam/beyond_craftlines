package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredRegistrationQueueTest
{
    @Test
    void deduplicatesPlacementsAndDrainsExactlyOnce()
    {
        DeferredRegistrationQueue<String> queue = new DeferredRegistrationQueue<>();

        queue.schedule("blast_furnace");
        queue.schedule("smoker");
        queue.schedule("blast_furnace");

        assertEquals(List.of("blast_furnace", "smoker"), queue.drain());
        assertTrue(queue.drain().isEmpty());
    }
}
