package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrialMeasurementSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_trial_measurements";
    private final Map<UUID, TrialMeasurementAccumulator> measurements = new HashMap<>();
    private final Map<UUID, TrialNetworkSnapshot> snapshots = new HashMap<>();

    public static TrialMeasurementSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TrialMeasurementSavedData::new, TrialMeasurementSavedData::load), NAME);
    }

    public static TrialMeasurementSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        TrialMeasurementSavedData data = new TrialMeasurementSavedData();
        CompoundTag entries = tag.getCompound("measurements");
        for (String key : entries.getAllKeys())
        {
            try
            {
                CompoundTag value = entries.getCompound(key);
                TrialMeasurementAccumulator accumulator = new TrialMeasurementAccumulator();
                CompoundTag inputs = value.getCompound("inputs");
                for (String item : inputs.getAllKeys()) accumulator.addInput(item, inputs.getLong(item));
                CompoundTag outputs = value.getCompound("outputs");
                for (String item : outputs.getAllKeys()) accumulator.addOutput(item, outputs.getLong(item));
                CompoundTag fluidInputs = value.getCompound("fluid_inputs");
                for (String fluid : fluidInputs.getAllKeys()) accumulator.addFluidInput(fluid, fluidInputs.getLong(fluid));
                CompoundTag fluidOutputs = value.getCompound("fluid_outputs");
                for (String fluid : fluidOutputs.getAllKeys()) accumulator.addFluidOutput(fluid, fluidOutputs.getLong(fluid));
                accumulator.addEnergy(value.getLong("energy"));
                if (value.getLong("cycle") > 0) accumulator.setCycleTicks(value.getLong("cycle"));
                data.measurements.put(UUID.fromString(key), accumulator);
                if (value.contains("snapshot"))
                {
                    CompoundTag snapshot = value.getCompound("snapshot");
                    data.snapshots.put(UUID.fromString(key), new TrialNetworkSnapshot(
                            snapshot.getInt("network"),
                            readAmounts(snapshot.getCompound("items")),
                            readAmounts(snapshot.getCompound("fluids")),
                            snapshot.getLong("energy")));
                }
            }
            catch (RuntimeException ignored) {}
        }
        return data;
    }

    public TrialMeasurementAccumulator get(UUID blueprintId) { return measurements.get(blueprintId); }

    public TrialNetworkSnapshot snapshot(UUID blueprintId) { return snapshots.get(blueprintId); }

    public void putSnapshot(UUID blueprintId, TrialNetworkSnapshot snapshot)
    {
        snapshots.put(blueprintId, snapshot);
        setDirty();
    }

    public void put(UUID blueprintId, TrialMeasurementAccumulator accumulator)
    {
        measurements.put(blueprintId, accumulator);
        setDirty();
    }

    public void remove(UUID blueprintId)
    {
        boolean changed = measurements.remove(blueprintId) != null;
        changed |= snapshots.remove(blueprintId) != null;
        if (changed) setDirty();
    }

    private static Map<String, Long> readAmounts(CompoundTag tag)
    {
        Map<String, Long> result = new HashMap<>();
        for (String key : tag.getAllKeys()) result.put(key, tag.getLong(key));
        return result;
    }

    private static CompoundTag writeAmounts(Map<String, Long> amounts)
    {
        CompoundTag tag = new CompoundTag();
        amounts.forEach((key, value) -> tag.putLong(key, value));
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        CompoundTag entries = new CompoundTag();
        for (Map.Entry<UUID, TrialMeasurementAccumulator> entry : measurements.entrySet())
        {
            TrialMeasurementAccumulator accumulator = entry.getValue();
            CompoundTag value = new CompoundTag();
            CompoundTag inputs = new CompoundTag();
            accumulator.inputs().forEach((item, amount) -> inputs.putLong(item, amount));
            value.put("inputs", inputs);
            CompoundTag outputs = new CompoundTag();
            accumulator.outputs().forEach((item, amount) -> outputs.putLong(item, amount));
            value.put("outputs", outputs);
            value.put("fluid_inputs", writeAmounts(accumulator.fluidInputs()));
            value.put("fluid_outputs", writeAmounts(accumulator.fluidOutputs()));
            value.putLong("energy", accumulator.energyNet());
            value.putLong("cycle", accumulator.cycleTicks());
            TrialNetworkSnapshot snapshot = snapshots.get(entry.getKey());
            if (snapshot != null)
            {
                CompoundTag snapshotTag = new CompoundTag();
                snapshotTag.putInt("network", snapshot.networkId());
                snapshotTag.put("items", writeAmounts(snapshot.items()));
                snapshotTag.put("fluids", writeAmounts(snapshot.fluids()));
                snapshotTag.putLong("energy", snapshot.energy());
                value.put("snapshot", snapshotTag);
            }
            entries.put(entry.getKey().toString(), value);
        }
        tag.put("measurements", entries);
        return tag;
    }
}
