package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SandboxSessionSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_sandbox_sessions";
    private final Map<UUID, SandboxSession> sessions = new HashMap<>();

    public static SandboxSessionSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SandboxSessionSavedData::new, SandboxSessionSavedData::load), NAME);
    }

    public static SandboxSessionSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        SandboxSessionSavedData data = new SandboxSessionSavedData();
        ListTag list = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            try
            {
                SandboxSlot slot = new SandboxSlot(entry.getInt("slot"), entry.getInt("x"), entry.getInt("y"),
                        entry.getInt("z"), entry.getInt("spacing"));
                SandboxSession session = new SandboxSession(entry.getUUID("id"), entry.getUUID("blueprint"),
                        entry.getUUID("owner"), slot, entry.getLong("created"));
                data.sessions.put(session.id(), session);
            }
            catch (RuntimeException ignored) {}
        }
        return data;
    }

    public java.util.List<SandboxSession> all() { return java.util.List.copyOf(sessions.values()); }
    public SandboxSession get(UUID id) { return sessions.get(id); }

    public void put(SandboxSession session)
    {
        sessions.put(session.id(), session);
        setDirty();
    }

    public SandboxSession remove(UUID id)
    {
        SandboxSession removed = sessions.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    public SandboxSession findByOwner(UUID owner)
    {
        return sessions.values().stream()
                .filter(session -> session.owner().equals(owner))
                .findFirst()
                .orElse(null);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (SandboxSession session : sessions.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", session.id());
            entry.putUUID("blueprint", session.blueprintId());
            entry.putUUID("owner", session.owner());
            entry.putInt("slot", session.slot().index());
            entry.putInt("x", session.slot().originX());
            entry.putInt("y", session.slot().originY());
            entry.putInt("z", session.slot().originZ());
            entry.putInt("spacing", session.slot().spacing());
            entry.putLong("created", session.createdAt());
            list.add(entry);
        }
        tag.put("sessions", list);
        return tag;
    }
}
