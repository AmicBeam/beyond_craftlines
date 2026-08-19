package com.amicbeam.beyondcraftlines.common.structure;

import java.util.UUID;

public record SandboxSession(UUID id, UUID blueprintId, UUID owner, SandboxSlot slot, long createdAt)
{
    public SandboxSession
    {
        if (id == null || blueprintId == null || owner == null || slot == null)
            throw new IllegalArgumentException("sandbox session fields are required");
    }
}
