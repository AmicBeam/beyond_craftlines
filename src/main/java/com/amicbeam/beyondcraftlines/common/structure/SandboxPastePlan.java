package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;

import java.util.List;

public record SandboxPastePlan(List<Placement> placements, List<BlockPos> barrierPositions)
{
    public SandboxPastePlan
    {
        placements = List.copyOf(placements);
        barrierPositions = List.copyOf(barrierPositions);
    }

    public record Placement(BlockPos position, String blockId, String state,
                             net.minecraft.nbt.CompoundTag blockEntityData)
    {
        public Placement(BlockPos position, String blockId, String state)
        {
            this(position, blockId, state, null);
        }
    }
}
