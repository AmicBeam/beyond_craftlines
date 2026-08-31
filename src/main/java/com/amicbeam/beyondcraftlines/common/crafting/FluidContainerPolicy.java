package com.amicbeam.beyondcraftlines.common.crafting;

/** Pure choice rule for the fluid proxy versus its concrete container item. */
final class FluidContainerPolicy
{
    private FluidContainerPolicy() {}
    static boolean useFluid(boolean rawFluid, boolean proxyAvailable,
                            boolean forceFluid, boolean forceItem, long availableFluid)
    { return rawFluid || proxyAvailable && !forceItem && (forceFluid || availableFluid > 0); }
}
