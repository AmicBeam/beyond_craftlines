package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record SandboxCleanupPlan(List<BlockPos> positions)
{
    public SandboxCleanupPlan
    {
        positions = List.copyOf(positions);
    }

    public static SandboxCleanupPlan from(SandboxPastePlan plan)
    {
        if (plan == null) throw new IllegalArgumentException("paste plan is required");
        List<BlockPos> positions = new ArrayList<>(plan.barrierPositions());
        plan.placements().stream()
                .map(SandboxPastePlan.Placement::position)
                .filter(position -> !positions.contains(position))
                .forEach(positions::add);
        return new SandboxCleanupPlan(positions);
    }
}
