package com.amicbeam.beyondcraftlines.client;

/** Screen-space geometry for editable nodes and compact, read-only tree previews. */
final class TreeViewScale
{
    static final double DEFAULT_ZOOM = 0.65;
    private static final int MIN_PERCENT = 25;
    private static final int MAX_PERCENT = 175;
    private static final int NORMAL_SIZE = 28;
    private static final double CENTER_OFFSET = NORMAL_SIZE / 2.0;

    private TreeViewScale() {}

    static boolean previewOnly(double zoom) { return zoom < 0.45; }

    static double nextZoom(double zoom, double scroll)
    {
        if (scroll == 0 || !Double.isFinite(scroll)) return zoom;
        int steps = (int) Math.min(15, Math.max(1, Math.ceil(Math.abs(scroll))));
        int percent = MIN_PERCENT + 10 * (int) Math.round((zoom * 100 - MIN_PERCENT) / 10);
        percent += scroll > 0 ? steps * 10 : -steps * 10;
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent)) / 100.0;
    }

    static int nodeSize(double zoom)
    { return previewOnly(zoom) ? (int) Math.floor(NORMAL_SIZE * zoom / DEFAULT_ZOOM) : NORMAL_SIZE; }

    static int nodeStart(double coordinate, double offset, double zoom, int stride)
    {
        // Keep the same logical center when switching from full frames to compact thumbnails.
        return (int) Math.floor(offset + coordinate * stride * zoom + CENTER_OFFSET - nodeSize(zoom) / 2.0);
    }

    static double offsetAfterZoom(double offset, double pointer, double oldZoom, double newZoom)
    { return pointer - CENTER_OFFSET - (pointer - CENTER_OFFSET - offset) * newZoom / oldZoom; }
}
