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

    @Test void clippedNodesCannotBeHoveredThroughTheLeftOrTopPanel()
    {
        assertTrue(ViewportCulling.intersects(10, 20, 110, 100, 0, 10, 30, 40));
        assertTrue(ViewportCulling.hitTest(10, 20, 110, 100, 0, 10, 30, 40, 15, 25));
        assertFalse(ViewportCulling.hitTest(10, 20, 110, 100, 0, 10, 30, 40, 5, 25));
        assertFalse(ViewportCulling.hitTest(10, 20, 110, 100, 0, 10, 30, 40, 15, 15));
    }

    @Test void clippedNodesCannotBeHoveredThroughTheSidebarOrFooter()
    {
        assertTrue(ViewportCulling.intersects(10, 20, 110, 100, 90, 70, 130, 110));
        assertTrue(ViewportCulling.hitTest(10, 20, 110, 100, 90, 70, 130, 110, 100, 90));
        assertFalse(ViewportCulling.hitTest(10, 20, 110, 100, 90, 70, 130, 110, 115, 80));
        assertFalse(ViewportCulling.hitTest(10, 20, 110, 100, 90, 70, 130, 110, 100, 105));
        assertFalse(ViewportCulling.hitTest(10, 20, 110, 100, 90, 70, 130, 110, 110, 80));
        assertFalse(ViewportCulling.hitTest(10, 20, 110, 100, 90, 70, 130, 110, 100, 100));
    }

    @Test void hitTestingStillRequiresThePointerToBeInsideTheNode()
    {
        assertFalse(ViewportCulling.hitTest(10, 20, 110, 100, 20, 30, 40, 50, 60, 60));
        assertTrue(ViewportCulling.hitTest(10, 20, 110, 100, 20, 30, 40, 50, 20.5, 30.5));
    }
}
