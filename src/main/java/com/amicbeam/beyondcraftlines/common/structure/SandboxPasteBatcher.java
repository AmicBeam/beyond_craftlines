package com.amicbeam.beyondcraftlines.common.structure;

import java.util.List;

public final class SandboxPasteBatcher
{
    private SandboxPasteBatcher() {}

    public static List<SandboxPastePlan.Placement> next(
            List<SandboxPastePlan.Placement> placements, int offset, int budget)
    {
        if (placements == null) throw new IllegalArgumentException("placements are required");
        if (offset < 0 || offset > placements.size()) throw new IllegalArgumentException("invalid offset");
        if (budget < 1) throw new IllegalArgumentException("budget must be positive");
        return List.copyOf(placements.subList(offset, Math.min(placements.size(), offset + budget)));
    }

    public static boolean hasMore(List<SandboxPastePlan.Placement> placements, int offset)
    {
        if (placements == null || offset < 0) throw new IllegalArgumentException("invalid paste state");
        return offset < placements.size();
    }
}
