package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.resources.ResourceLocation;

public record FluidAmount(ResourceLocation fluidId, long amount)
{
    public FluidAmount
    {
        if (fluidId == null) throw new IllegalArgumentException("fluid ID is required");
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
    }
}
