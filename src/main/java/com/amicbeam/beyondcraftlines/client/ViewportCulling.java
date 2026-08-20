package com.amicbeam.beyondcraftlines.client;

final class ViewportCulling
{
    private ViewportCulling() {}

    static boolean intersects(int viewportLeft, int viewportTop, int viewportRight, int viewportBottom,
                              int left, int top, int right, int bottom)
    {
        return right > viewportLeft && left < viewportRight
                && bottom > viewportTop && top < viewportBottom;
    }
}
