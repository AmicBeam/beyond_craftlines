package com.amicbeam.beyondcraftlines.client;

final class ViewportCulling
{
    private ViewportCulling() {}

    static boolean containsPoint(int left, int top, int right, int bottom, double x, double y)
    { return x >= left && x < right && y >= top && y < bottom; }

    static boolean hitTest(int viewportLeft, int viewportTop, int viewportRight, int viewportBottom,
                           int left, int top, int right, int bottom, double x, double y)
    {
        return containsPoint(viewportLeft, viewportTop, viewportRight, viewportBottom, x, y)
                && containsPoint(left, top, right, bottom, x, y);
    }

    static boolean intersects(int viewportLeft, int viewportTop, int viewportRight, int viewportBottom,
                              int left, int top, int right, int bottom)
    {
        return right > viewportLeft && left < viewportRight
                && bottom > viewportTop && top < viewportBottom;
    }
}
