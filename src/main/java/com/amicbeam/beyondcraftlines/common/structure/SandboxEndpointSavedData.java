package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SandboxEndpointSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_sandbox_endpoints";
    private final Map<UUID, List<SkyLogisticsEndpoint>> endpoints = new HashMap<>();

    public static SandboxEndpointSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SandboxEndpointSavedData::new, SandboxEndpointSavedData::load), NAME);
    }

    public static SandboxEndpointSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        SandboxEndpointSavedData data = new SandboxEndpointSavedData();
        ListTag sessions = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < sessions.size(); i++)
        {
            CompoundTag session = sessions.getCompound(i);
            try
            {
                UUID id = session.getUUID("id");
                ListTag list = session.getList("endpoints", Tag.TAG_COMPOUND);
                java.util.ArrayList<SkyLogisticsEndpoint> values = new java.util.ArrayList<>();
                for (int j = 0; j < list.size(); j++)
                {
                    CompoundTag endpoint = list.getCompound(j);
                    values.add(new SkyLogisticsEndpoint(
                            new BlockPos(endpoint.getInt("x"), endpoint.getInt("y"), endpoint.getInt("z")),
                            endpoint.getInt("network"),
                            Direction.byName(endpoint.getString("direction")),
                            endpoint.getBoolean("items"), endpoint.getBoolean("fluids"),
                            endpoint.getBoolean("energy")));
                }
                data.endpoints.put(id, List.copyOf(values));
            }
            catch (RuntimeException ignored) {}
        }
        return data;
    }

    public List<SkyLogisticsEndpoint> get(UUID sessionId)
    {
        return endpoints.getOrDefault(sessionId, List.of());
    }

    public List<SkyLogisticsEndpoint> enabledFor(UUID sessionId, boolean items, boolean fluids, boolean energy)
    {
        return get(sessionId).stream()
                .filter(endpoint -> !items || endpoint.items())
                .filter(endpoint -> !fluids || endpoint.fluids())
                .filter(endpoint -> !energy || endpoint.energy())
                .toList();
    }

    public void put(UUID sessionId, List<SkyLogisticsEndpoint> values)
    {
        endpoints.put(sessionId, List.copyOf(values));
        setDirty();
    }

    public void remove(UUID sessionId)
    {
        if (endpoints.remove(sessionId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag sessions = new ListTag();
        for (var entry : endpoints.entrySet())
        {
            CompoundTag session = new CompoundTag();
            session.putUUID("id", entry.getKey());
            ListTag values = new ListTag();
            for (SkyLogisticsEndpoint endpoint : entry.getValue())
            {
                CompoundTag value = new CompoundTag();
                value.putInt("x", endpoint.position().getX());
                value.putInt("y", endpoint.position().getY());
                value.putInt("z", endpoint.position().getZ());
                value.putInt("network", endpoint.networkId());
                value.putString("direction", endpoint.direction().getName());
                value.putBoolean("items", endpoint.items());
                value.putBoolean("fluids", endpoint.fluids());
                value.putBoolean("energy", endpoint.energy());
                values.add(value);
            }
            session.put("endpoints", values);
            sessions.add(session);
        }
        tag.put("sessions", sessions);
        return tag;
    }
}
