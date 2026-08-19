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

public final class TrialSessionSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_trial_sessions";
    private final Map<UUID, TrialSession> sessions = new HashMap<>();

    public static TrialSessionSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TrialSessionSavedData::new, TrialSessionSavedData::load), NAME);
    }

    public static TrialSessionSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        TrialSessionSavedData data = new TrialSessionSavedData();
        ListTag list = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            try
            {
                TrialSession session = TrialSessionCodec.read(list.getCompound(i));
                data.sessions.put(session.blueprintId(), session);
            }
            catch (RuntimeException ignored) {}
        }
        return data;
    }

    public TrialSession get(UUID blueprintId) { return sessions.get(blueprintId); }

    public java.util.List<TrialSession> all() { return java.util.List.copyOf(sessions.values()); }

    public void put(TrialSession session)
    {
        sessions.put(session.blueprintId(), session);
        setDirty();
    }

    public void remove(UUID blueprintId)
    {
        if (sessions.remove(blueprintId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (TrialSession session : sessions.values()) list.add(TrialSessionCodec.write(session));
        tag.put("sessions", list);
        return tag;
    }
}
