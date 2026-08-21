package com.amicbeam.beyondcraftlines.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;

public final class ForgeCapabilityAccess {
    private ForgeCapabilityAccess() {}
    public static <T> T get(ServerLevel level, Capability<T> capability, BlockPos position, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity == null ? null : blockEntity.getCapability(capability, side).resolve().orElse(null);
    }
}
