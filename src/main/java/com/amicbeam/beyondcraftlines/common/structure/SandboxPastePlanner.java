package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class SandboxPastePlanner
{
    private static final int BARRIER_PADDING = 2;

    private SandboxPastePlanner() {}

    public static SandboxPastePlan plan(StructureSnapshot snapshot, SandboxSlot slot)
    {
        if (snapshot == null || slot == null) throw new IllegalArgumentException("snapshot and slot are required");
        List<SandboxPastePlan.Placement> placements = new ArrayList<>();
        for (StructureSnapshot.BlockEntry block : snapshot.blocks())
        {
            BlockPos position = new BlockPos(slot.originX() + block.relativePos().getX(),
                    slot.originY() + block.relativePos().getY(),
                    slot.originZ() + block.relativePos().getZ());
            placements.add(new SandboxPastePlan.Placement(position, block.blockId().toString(), block.state(),
                    block.blockEntityData() == null ? null : block.blockEntityData().copy()));
        }
        List<BlockPos> barriers = new ArrayList<>();
        int minX = slot.originX() - BARRIER_PADDING;
        int minY = slot.originY() - BARRIER_PADDING;
        int minZ = slot.originZ() - BARRIER_PADDING;
        int maxX = slot.maxX(snapshot.size().getX()) + BARRIER_PADDING;
        int maxY = slot.maxY(snapshot.size().getY()) + BARRIER_PADDING;
        int maxZ = slot.maxZ(snapshot.size().getZ()) + BARRIER_PADDING;
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    if (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ)
                        barriers.add(new BlockPos(x, y, z));
        return new SandboxPastePlan(placements, barriers);
    }
}
