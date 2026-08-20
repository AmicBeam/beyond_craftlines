package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewportCullingTest
{
    @Test
    void acceptsContainedAndPartiallyOverlappingBounds()
    {
        assertTrue(ViewportCulling.intersects(10, 10, 100, 100, 20, 20, 30, 30));
        assertTrue(ViewportCulling.intersects(10, 10, 100, 100, 0, 20, 20, 30));
    }

    @Test
    void rejectsOutsideAndMerelyTouchingBounds()
    {
        assertFalse(ViewportCulling.intersects(10, 10, 100, 100, 0, 20, 10, 30));
        assertFalse(ViewportCulling.intersects(10, 10, 100, 100, 100, 20, 110, 30));
        assertFalse(ViewportCulling.intersects(10, 10, 100, 100, 20, 0, 30, 10));
        assertFalse(ViewportCulling.intersects(10, 10, 100, 100, 20, 100, 30, 110));
    }
}
