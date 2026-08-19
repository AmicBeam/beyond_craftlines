package com.amicbeam.beyondcraftlines.common.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProductionJobSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_production_jobs";
    private final Map<UUID, ProductionJob> jobs = new HashMap<>();

    public static ProductionJobSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ProductionJobSavedData::new, ProductionJobSavedData::load), NAME);
    }

    public static ProductionJobSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        ProductionJobSavedData data = new ProductionJobSavedData();
        ListTag list = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag value = list.getCompound(i);
            try
            {
                ExecutorState state = new ExecutorState(ExecutorState.Status.valueOf(value.getString("status")),
                        value.getLong("started"), value.getLong("finish"), value.getString("hash"));
                ProductionJob job = new ProductionJob(value.getUUID("id"), value.getUUID("blueprint"),
                        value.getUUID("owner"), value.getInt("network"), value.getInt("remaining"), state,
                        value.getString("failure"));
                data.jobs.put(job.id(), job);
            }
            catch (RuntimeException ignored) {}
        }
        return data;
    }

    public java.util.List<ProductionJob> all() { return java.util.List.copyOf(jobs.values()); }
    public ProductionJob get(UUID id) { return jobs.get(id); }

    public void put(ProductionJob job) { jobs.put(job.id(), job); setDirty(); }
    public ProductionJob remove(UUID id) { ProductionJob job = jobs.remove(id); if (job != null) setDirty(); return job; }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (ProductionJob job : jobs.values())
        {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", job.id());
            value.putUUID("blueprint", job.blueprintId());
            value.putUUID("owner", job.owner());
            value.putInt("network", job.networkId());
            value.putInt("remaining", job.remaining());
            value.putString("status", job.state().status().name());
            value.putLong("started", job.state().startedAt());
            value.putLong("finish", job.state().finishAt());
            value.putString("hash", job.state().blueprintHash());
            value.putString("failure", job.failure());
            list.add(value);
        }
        tag.put("jobs", list);
        return tag;
    }
}
