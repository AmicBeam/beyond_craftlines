package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SandboxPasteJobSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_sandbox_paste_jobs";
    private final Map<UUID, Integer> offsets = new HashMap<>();

    public static SandboxPasteJobSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SandboxPasteJobSavedData::new, SandboxPasteJobSavedData::load), NAME);
    }

    public static SandboxPasteJobSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        SandboxPasteJobSavedData data = new SandboxPasteJobSavedData();
        CompoundTag jobs = tag.getCompound("jobs");
        for (String key : jobs.getAllKeys())
        {
            try { data.offsets.put(UUID.fromString(key), jobs.getInt(key)); }
            catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    public int offset(UUID session) { return offsets.getOrDefault(session, 0); }

    public void put(UUID session, int offset)
    {
        offsets.put(session, offset);
        setDirty();
    }

    public void remove(UUID session)
    {
        if (offsets.remove(session) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        CompoundTag jobs = new CompoundTag();
        offsets.forEach((session, offset) -> jobs.putInt(session.toString(), offset));
        tag.put("jobs", jobs);
        return tag;
    }
}
