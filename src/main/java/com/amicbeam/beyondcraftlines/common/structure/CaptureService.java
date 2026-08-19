package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

public final class CaptureService
{
    public static final int MAX_VOLUME = 32768;
    private static final Set<ResourceLocation> DEFAULT_BLACKLIST = Set.of(
            ResourceLocation.withDefaultNamespace("command_block"),
            ResourceLocation.withDefaultNamespace("chain_command_block"),
            ResourceLocation.withDefaultNamespace("repeating_command_block"),
            ResourceLocation.withDefaultNamespace("structure_block"),
            ResourceLocation.withDefaultNamespace("jigsaw"),
            ResourceLocation.withDefaultNamespace("end_portal_frame"));

    private CaptureService() {}

    public static Result validate(Level level, BlockPos first, BlockPos second)
    {
        int width = Math.abs(first.getX() - second.getX()) + 1;
        int height = Math.abs(first.getY() - second.getY()) + 1;
        int depth = Math.abs(first.getZ() - second.getZ()) + 1;
        long volume = (long) width * height * depth;
        if (volume > MAX_VOLUME) return Result.failure("volume");
        for (BlockPos pos : BlockPos.betweenClosed(first, second))
        {
            if (DEFAULT_BLACKLIST.contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(pos).getBlock()))) return Result.failure("blacklist");
        }
        return Result.success();
    }

    public record Result(boolean valid, String reason)
    {
        static Result success() { return new Result(true, ""); }
        static Result failure(String reason) { return new Result(false, reason); }
    }
}
