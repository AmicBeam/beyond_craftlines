package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FluidContainerPolicyTest
{
    @Test void fluidIsPreferredWhenAvailableButBucketRemainsAFallback()
    {
        assertTrue(FluidContainerPolicy.useFluid(false, true, false, false, 1000));
        assertFalse(FluidContainerPolicy.useFluid(false, true, false, false, 0));
    }

    @Test void explicitChoicesOverrideAutomaticAvailability()
    {
        assertFalse(FluidContainerPolicy.useFluid(false, true, false, true, 1000));
        assertTrue(FluidContainerPolicy.useFluid(false, true, true, false, 0));
        assertTrue(FluidContainerPolicy.useFluid(true, false, false, false, 0));
    }
}
