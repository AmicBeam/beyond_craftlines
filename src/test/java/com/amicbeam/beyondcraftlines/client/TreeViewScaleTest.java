package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class TreeViewScaleTest
{
    @Test void reachesBothPreviewLevelsAndReturnsToEditableScale()
    {
        double zoom = TreeViewScale.nextZoom(0.45, -1);
        assertEquals(0.35, zoom);
        assertTrue(TreeViewScale.previewOnly(zoom));
        zoom = TreeViewScale.nextZoom(zoom, -1);
        assertEquals(0.25, zoom);
        assertEquals(0.25, TreeViewScale.nextZoom(zoom, -1));
        assertEquals(0.35, TreeViewScale.nextZoom(zoom, 1));
        zoom = TreeViewScale.nextZoom(0.35, 1);
        assertEquals(0.45, zoom);
        assertFalse(TreeViewScale.previewOnly(zoom));
        assertEquals(28, TreeViewScale.nodeSize(zoom));
    }

    @Test void wheelAndTrackpadDeltasUseExactLevelsWithinTheBounds()
    {
        assertEquals(0.35, TreeViewScale.nextZoom(0.45, -0.05));
        assertEquals(0.25, TreeViewScale.nextZoom(0.65, -100));
        assertEquals(1.75, TreeViewScale.nextZoom(0.65, 100));
        assertEquals(0.65, TreeViewScale.nextZoom(0.65, 0));
        assertEquals(0.65, TreeViewScale.nextZoom(0.65, Double.NaN));
    }

    @Test void compactFramesNeverOverlapAdjacentRowsOrColumnsAfterPixelRounding()
    {
        for (double zoom : new double[]{0.25, 0.35})
        {
            int size = TreeViewScale.nodeSize(zoom);
            assertTrue(size >= 10); // Keep a visible border and at least an 8px thumbnail.
            for (double offset : new double[]{-83.7, -0.5, 0, 0.9, 45.8})
                for (int index = 0; index < 100; index++)
                    for (int stride : new int[]{46, 58})
                    {
                        int first = TreeViewScale.nodeStart(index, offset, zoom, stride);
                        int second = TreeViewScale.nodeStart(index + 1, offset, zoom, stride);
                        assertTrue(first + size < second, "Preview frames need a visible gap");
                    }
        }
    }

    @Test void compactHoverBoundsDoNotReachIntoTheNeighbourOrHiddenPartOfTheCanvas()
    {
        for (double zoom : new double[]{0.25, 0.35})
        {
            int size = TreeViewScale.nodeSize(zoom);
            int x = TreeViewScale.nodeStart(0, -8, zoom, 46);
            int next = TreeViewScale.nodeStart(1, -8, zoom, 46);
            assertTrue(ViewportCulling.hitTest(0, 0, 100, 100, x, 10, x + size, 10 + size, Math.max(0, x) + 1, 11));
            assertFalse(ViewportCulling.hitTest(0, 0, 100, 100, x, 10, x + size, 10 + size, next, 11));
            assertFalse(ViewportCulling.hitTest(0, 0, 100, 100, x, 10, x + size, 10 + size, -1, 11));
        }
    }

    @Test void zoomingThroughThePreviewBoundaryKeepsTheLogicalPointUnderTheCursor()
    {
        double pointer = 187.25;
        double offset = -31.5;
        double zoom = 0.45;
        double coordinate = (pointer - 14 - offset) / (46 * zoom);
        for (double next : new double[]{0.35, 0.25, 0.35, 0.45, 0.65})
        {
            offset = TreeViewScale.offsetAfterZoom(offset, pointer, zoom, next);
            assertEquals(pointer, offset + 14 + coordinate * 46 * next, 1e-9);
            int start = TreeViewScale.nodeStart(coordinate, offset, next, 46);
            assertEquals(pointer, start + TreeViewScale.nodeSize(next) / 2.0, 1.0);
            zoom = next;
        }
    }
}
