package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class SandboxCleanupExecutor
{
    private SandboxCleanupExecutor() {}

    public static void clear(ServerLevel level, SandboxPastePlan plan)
    {
        if (level == null || plan == null)
            throw new IllegalArgumentException("level and cleanup plan are required");
        for (BlockPos position : SandboxCleanupPlan.from(plan).positions())
        {
            if (level.getBlockEntity(position) != null)
                level.removeBlockEntity(position);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
