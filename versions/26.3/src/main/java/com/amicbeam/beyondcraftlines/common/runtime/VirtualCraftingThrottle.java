package com.amicbeam.beyondcraftlines.common.runtime;

/** Pure timing rules for simulated vanilla crafting-tree nodes. */
final class VirtualCraftingThrottle
{
    private VirtualCraftingThrottle() {}

    static boolean ready(long gameTime, long nextAllowedTick)
    {
        return gameTime >= nextAllowedTick;
    }

    static long nextAllowedTick(long gameTime, int intervalTicks)
    {
        if (gameTime < 0 || intervalTicks < 1) throw new IllegalArgumentException("invalid crafting interval");
        return Long.MAX_VALUE - gameTime < intervalTicks ? Long.MAX_VALUE : gameTime + intervalTicks;
    }
}
