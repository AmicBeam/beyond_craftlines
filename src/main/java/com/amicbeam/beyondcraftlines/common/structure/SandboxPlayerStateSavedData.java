package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SandboxPlayerStateSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_sandbox_players";
    private final Map<UUID, SandboxPlayerState> states = new HashMap<>();

    public static SandboxPlayerStateSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SandboxPlayerStateSavedData::new, SandboxPlayerStateSavedData::load), NAME);
    }

    public static SandboxPlayerStateSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        SandboxPlayerStateSavedData data = new SandboxPlayerStateSavedData();
        ListTag list = tag.getList("states", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            try
            {
                SandboxPlayerState state = new SandboxPlayerState(entry.getUUID("player"), entry.getUUID("session"),
                        entry.getString("dimension"), entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                        entry.getFloat("yaw"), entry.getFloat("pitch"), GameType.byId(entry.getInt("game_type")));
                data.states.put(state.player(), state);
            }
            catch (RuntimeException ignored) {}
        }
        return data;
    }

    public SandboxPlayerState get(UUID player) { return states.get(player); }

    public void put(SandboxPlayerState state)
    {
        states.put(state.player(), state);
        setDirty();
    }

    public SandboxPlayerState remove(UUID player)
    {
        SandboxPlayerState removed = states.remove(player);
        if (removed != null) setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (SandboxPlayerState state : states.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", state.player());
            entry.putUUID("session", state.session());
            entry.putString("dimension", state.dimension());
            entry.putDouble("x", state.x());
            entry.putDouble("y", state.y());
            entry.putDouble("z", state.z());
            entry.putFloat("yaw", state.yaw());
            entry.putFloat("pitch", state.pitch());
            entry.putInt("game_type", state.gameType().getId());
            list.add(entry);
        }
        tag.put("states", list);
        return tag;
    }
}
