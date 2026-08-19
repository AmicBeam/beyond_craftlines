package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.resources.ResourceLocation;

public record ResourceAmount(ResourceLocation itemId, long amount)
{
    public ResourceAmount
    {
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
    }
}
