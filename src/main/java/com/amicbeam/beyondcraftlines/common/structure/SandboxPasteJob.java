package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.level.ServerLevel;

public final class SandboxPasteJob
{
    private static final int DEFAULT_BUDGET = 256;

    private final SandboxPastePlan plan;
    private int placementOffset;
    private boolean barriersPlaced;

    public SandboxPasteJob(SandboxPastePlan plan)
    {
        this(plan, 0);
    }

    public SandboxPasteJob(SandboxPastePlan plan, int placementOffset)
    {
        if (plan == null) throw new IllegalArgumentException("paste plan is required");
        if (placementOffset < 0 || placementOffset > plan.placements().size())
            throw new IllegalArgumentException("invalid placement offset");
        this.plan = plan;
        this.placementOffset = placementOffset;
    }

    public boolean tick(ServerLevel level)
    {
        return tick(level, DEFAULT_BUDGET);
    }

    public boolean tick(ServerLevel level, int budget)
    {
        if (level == null) throw new IllegalArgumentException("level is required");
        for (SandboxPastePlan.Placement placement :
                SandboxPasteBatcher.next(plan.placements(), placementOffset, budget))
        {
            SandboxPasteExecutor.place(level, placement);
            placementOffset++;
        }
        if (!SandboxPasteBatcher.hasMore(plan.placements(), placementOffset) && !barriersPlaced)
        {
            SandboxPasteExecutor.placeBarriers(level, plan);
            barriersPlaced = true;
        }
        return barriersPlaced;
    }

    public int placementOffset() { return placementOffset; }
    public boolean complete() { return barriersPlaced; }
}
