package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

final class SandboxPastePlannerTest
{
    @Test
    void rejectsMissingSnapshotOrSlotWithoutMinecraftRuntime()
    {
        assertThrows(IllegalArgumentException.class,
                () -> SandboxPastePlanner.plan(null, new SandboxSlot(0, 0, 64, 0, 512)));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxPastePlanner.plan(null, null));
    }
}
