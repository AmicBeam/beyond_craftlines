package com.amicbeam.beyondcraftlines.common.dashboard;

public final class AutomaticOrderPolicy
{
    private AutomaticOrderPolicy() {}

    public static long deficit(long target, long stored)
    { return target <= 0 ? 0 : Math.max(0, target - Math.max(0, stored)); }

    public static long transferable(long deficit, long insertCapacity)
    { return Math.min(Math.max(0, deficit), Math.max(0, insertCapacity)); }

    public static boolean canCreate(int activeOnNetwork, int maximum)
    { return maximum > 0 && activeOnNetwork < maximum; }
}
