package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class SandboxManager
{
    private static final int DEFAULT_SPACING = 512;
    private static final int DEFAULT_COLUMNS = 8;

    private SandboxManager() {}

    public static SandboxSession allocate(MinecraftServer server, UUID blueprintId, UUID owner)
    {
        SandboxSessionSavedData data = SandboxSessionSavedData.get(server);
        SandboxSlotAllocator allocator = new SandboxSlotAllocator(DEFAULT_SPACING, DEFAULT_COLUMNS);
        for (SandboxSession existing : data.all()) allocator.allocate();
        SandboxSlot slot = allocator.allocate();
        SandboxSession session = new SandboxSession(UUID.randomUUID(), blueprintId, owner, slot,
                server.overworld().getGameTime());
        data.put(session);
        return session;
    }

    public static SandboxSession allocateAndPaste(MinecraftServer server, ServerLevel sandboxLevel,
                                                    BlueprintRecord record, UUID owner)
    {
        if (sandboxLevel == null || !sandboxLevel.dimension().equals(SandboxDimension.KEY))
            throw new IllegalArgumentException("dedicated sandbox dimension is required");
        if (record == null) throw new IllegalArgumentException("blueprint record is required");
        SandboxSession session = allocate(server, record.id(), owner);
        try
        {
            SandboxPastePlan plan = SandboxPastePlanner.plan(record.snapshot(), session.slot());
            SandboxPasteExecutor.paste(sandboxLevel, plan);
            return session;
        }
        catch (RuntimeException exception)
        {
            SandboxSessionSavedData.get(server).remove(session.id());
            throw exception;
        }
    }

    public static SandboxSession release(MinecraftServer server, UUID sessionId, UUID owner)
    {
        SandboxSessionSavedData data = SandboxSessionSavedData.get(server);
        SandboxSession session = data.get(sessionId);
        if (session == null || !session.owner().equals(owner))
            throw new IllegalArgumentException("sandbox session not found or not owned by actor");
        return data.remove(sessionId);
    }
}
