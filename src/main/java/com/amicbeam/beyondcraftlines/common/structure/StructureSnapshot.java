package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;

public record StructureSnapshot(
        BlockPos size,
        List<BlockEntry> blocks,
        Map<String, Integer> itemTotals,
        String hash
) {
    public record BlockEntry(BlockPos relativePos, ResourceLocation blockId, String state,
                             CompoundTag blockEntityData) {
        public BlockEntry(BlockPos relativePos, ResourceLocation blockId, String state) {
            this(relativePos, blockId, state, null);
        }
    }
}
