package com.amicbeam.beyondcraftlines.common.structure;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SandboxPasteExecutor
{
    private SandboxPasteExecutor() {}

    public static void paste(ServerLevel level, SandboxPastePlan plan)
    {
        if (level == null || plan == null) throw new IllegalArgumentException("level and paste plan are required");
        SandboxPasteJob job = new SandboxPasteJob(plan);
        while (!job.tick(level)) {}
    }

    static void place(ServerLevel level, SandboxPastePlan.Placement placement)
    {
        BlockState state = parseState(level, placement);
        if (!state.isAir())
        {
            level.setBlock(placement.position(), state, 3);
            if (placement.blockEntityData() != null
                    && level.getBlockEntity(placement.position()) != null)
            {
                level.getBlockEntity(placement.position()).loadWithComponents(
                        placement.blockEntityData(), level.registryAccess());
                level.getBlockEntity(placement.position()).setChanged();
            }
        }
    }

    static void placeBarriers(ServerLevel level, SandboxPastePlan plan)
    {
        for (BlockPos position : plan.barrierPositions())
            level.setBlock(position, Blocks.BARRIER.defaultBlockState(), 3);
    }

    private static BlockState parseState(ServerLevel level, SandboxPastePlan.Placement placement)
    {
        String serialized = placement.state();
        if (serialized == null || serialized.isBlank()) serialized = placement.blockId();
        try
        {
            return BlockStateParser.parseForBlock(
                    level.registryAccess().lookupOrThrow(Registries.BLOCK), serialized, false).blockState();
        }
        catch (CommandSyntaxException | RuntimeException exception)
        {
            throw new IllegalArgumentException("invalid captured block state: " + serialized, exception);
        }
    }
}
