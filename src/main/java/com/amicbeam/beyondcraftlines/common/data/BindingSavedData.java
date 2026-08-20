package com.amicbeam.beyondcraftlines.common.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BindingSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_bindings";
    private final List<BindingRecord> records = new ArrayList<>();
    private final Map<UUID, List<BindingRecord>> byPlayer = new HashMap<>();
    private final Map<Integer, List<BindingRecord>> byNetwork = new HashMap<>();
    private final Map<BindingKey, BindingRecord> byPosition = new HashMap<>();
    private final Map<BindingKey, List<BindingRecord>> byProvisionerEndpoint = new HashMap<>();

    public static BindingSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        BindingSavedData data = new BindingSavedData();
        ListTag list = tag.getList("bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            BindingRecord record = readRecord(list.getCompound(i));
            if (record != null && (record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                    || record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING)) data.addLoaded(record);
        }
        if (data.records.size() != list.size()) data.setDirty();
        return data;
    }

    public static BindingSavedData get(net.minecraft.server.MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(BindingSavedData::new, BindingSavedData::load), NAME);
    }

    public List<BindingRecord> records() { return List.copyOf(records); }
    public List<BindingRecord> forPlayer(UUID player) { return List.copyOf(byPlayer.getOrDefault(player, List.of())); }
    public List<BindingRecord> forNetwork(int networkId) { return List.copyOf(byNetwork.getOrDefault(networkId, List.of())); }
    public Set<ResourceLocation> recipeTypesForProvisioner(ResourceKey<Level> dimension, BlockPos position)
    {
        HashSet<ResourceLocation> result = new HashSet<>();
        provisionerRecords(dimension, position).forEach(record -> result.addAll(record.jeiRecipeTypes()));
        return Set.copyOf(result);
    }
    public Set<String> recipeFamiliesForProvisioner(ResourceKey<Level> dimension, BlockPos position)
    {
        HashSet<String> result = new HashSet<>();
        provisionerRecords(dimension, position).forEach(record -> result.addAll(record.recipeFamilies()));
        return Set.copyOf(result);
    }
    public BindingRecord at(ResourceKey<Level> dimension, BlockPos position)
    {
        return byPosition.get(new BindingKey(dimension, position));
    }

    public void add(BindingRecord record)
    {
        BindingRecord previous = byPosition.get(new BindingKey(record.dimension(), record.position()));
        if (previous != null) removeInternal(previous);
        records.add(record);
        index(record);
        setDirty();
    }

    public boolean remove(ResourceKey<Level> dimension, BlockPos position)
    {
        BindingRecord record = byPosition.get(new BindingKey(dimension, position));
        if (record == null) return false;
        removeInternal(record);
        setDirty();
        return true;
    }

    public boolean removeForProvisioner(ResourceKey<Level> dimension, BlockPos position)
    {
        List<BindingRecord> matches = provisionerRecords(dimension, position);
        matches.forEach(this::removeInternal);
        if (!matches.isEmpty()) setDirty();
        return !matches.isEmpty();
    }

    public void replaceProvisionerBinding(ResourceKey<Level> dimension, BlockPos position,
                                           BindingRecord replacement)
    {
        provisionerRecords(dimension, position).forEach(this::removeInternal);
        records.add(replacement);
        index(replacement);
        setDirty();
    }

    private List<BindingRecord> provisionerRecords(ResourceKey<Level> dimension, BlockPos position)
    {
        return List.copyOf(byProvisionerEndpoint.getOrDefault(new BindingKey(dimension, position), List.of()));
    }

    private void addLoaded(BindingRecord record)
    {
        if (byPosition.containsKey(new BindingKey(record.dimension(), record.position()))) return;
        records.add(record);
        index(record);
    }

    private void index(BindingRecord record)
    {
        byPlayer.computeIfAbsent(record.owner(), ignored -> new ArrayList<>()).add(record);
        byNetwork.computeIfAbsent(record.networkId(), ignored -> new ArrayList<>()).add(record);
        byPosition.put(new BindingKey(record.dimension(), record.position()), record);
        if (record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING)
        {
            BindingKey primary = new BindingKey(record.dimension(), record.position());
            byProvisionerEndpoint.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(record);
            if (record.provisionerDimension() != null && record.provisionerPosition() != null)
            {
                BindingKey provisioner = new BindingKey(record.provisionerDimension(), record.provisionerPosition());
                if (!provisioner.equals(primary))
                    byProvisionerEndpoint.computeIfAbsent(provisioner, ignored -> new ArrayList<>()).add(record);
            }
        }
    }

    private void removeInternal(BindingRecord record)
    {
        records.remove(record);
        removeFrom(byPlayer, record.owner(), record);
        removeFrom(byNetwork, record.networkId(), record);
        byPosition.remove(new BindingKey(record.dimension(), record.position()));
        removeFrom(byProvisionerEndpoint, new BindingKey(record.dimension(), record.position()), record);
        if (record.provisionerDimension() != null && record.provisionerPosition() != null)
            removeFrom(byProvisionerEndpoint,
                    new BindingKey(record.provisionerDimension(), record.provisionerPosition()), record);
    }

    private static <K> void removeFrom(Map<K, List<BindingRecord>> index, K key, BindingRecord record)
    {
        List<BindingRecord> values = index.get(key);
        if (values != null)
        {
            values.remove(record);
            if (values.isEmpty()) index.remove(key);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (BindingRecord record : records) list.add(writeRecord(record));
        tag.put("bindings", list);
        return tag;
    }

    private static CompoundTag writeRecord(BindingRecord record)
    {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("id", record.id());
        entry.putUUID("owner", record.owner());
        entry.putInt("network", record.networkId());
        entry.putString("dimension", record.dimension().location().toString());
        entry.putInt("x", record.position().getX());
        entry.putInt("y", record.position().getY());
        entry.putInt("z", record.position().getZ());
        entry.putString("device", record.deviceType().name());
        entry.putString("block", record.lastBlockId().toString());
        if (record.provisionerDimension() != null && record.provisionerPosition() != null)
        {
            entry.putString("provisioner_dimension", record.provisionerDimension().location().toString());
            entry.putLong("provisioner_position", record.provisionerPosition().asLong());
        }
        entry.putString("name", record.nickname());
        entry.putBoolean("favorite", record.favorite());
        entry.putLong("time", record.boundGameTime());
        ListTag families = new ListTag();
        record.recipeFamilies().forEach(family -> families.add(StringTag.valueOf(family)));
        entry.put("families", families);
        ListTag jeiTypes = new ListTag();
        record.jeiRecipeTypes().forEach(type -> jeiTypes.add(StringTag.valueOf(type.toString())));
        entry.put("jei_types", jeiTypes);
        return entry;
    }

    private static BindingRecord readRecord(CompoundTag entry)
    {
        try
        {
            Set<String> families = new HashSet<>();
            ListTag familyList = entry.getList("families", Tag.TAG_STRING);
            for (int i = 0; i < familyList.size(); i++) families.add(familyList.getString(i));
            Set<ResourceLocation> jeiTypes = new HashSet<>();
            ListTag jeiTypeList = entry.getList("jei_types", Tag.TAG_STRING);
            for (int i = 0; i < jeiTypeList.size(); i++)
            {
                ResourceLocation type = ResourceLocation.tryParse(jeiTypeList.getString(i));
                if (type != null) jeiTypes.add(type);
            }
            ResourceKey<Level> provisionerDimension = entry.contains("provisioner_dimension", Tag.TAG_STRING)
                    ? ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(entry.getString("provisioner_dimension"))) : null;
            BlockPos provisionerPosition = entry.contains("provisioner_position", Tag.TAG_LONG)
                    ? BlockPos.of(entry.getLong("provisioner_position")) : null;
            return new BindingRecord(
                    entry.getUUID("id"), entry.getUUID("owner"), entry.getInt("network"),
                    ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            ResourceLocation.parse(entry.getString("dimension"))),
                    new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")),
                    readDeviceType(entry.getString("device")), jeiTypes, families,
                    ResourceLocation.parse(entry.getString("block")), provisionerDimension, provisionerPosition,
                    entry.getString("name"),
                    entry.getBoolean("favorite"), entry.getLong("time"));
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private static DeviceType readDeviceType(String name)
    {
        if ("PROVISIONER_RECIPE_TARGET".equals(name)) return DeviceType.PROVISIONER_RECIPE_BINDING;
        return DeviceType.valueOf(name);
    }

    private record BindingKey(ResourceKey<Level> dimension, BlockPos position) {}
}
