package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SkyLogisticsEndpoint(BlockPos position, int networkId, Direction direction,
                                   boolean items, boolean fluids, boolean energy)
{
    public SkyLogisticsEndpoint
    {
        if (position == null || direction == null) throw new IllegalArgumentException("endpoint fields are required");
    }
}
