package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.util.NbtCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

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
    private static final SavedDataType<BindingSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(BeyondCraftlines.MOD_ID, NAME),
            BindingSavedData::new,
            CompoundTag.CODEC.xmap(
                    tag -> load(tag, NbtCompat.builtinRegistries()),
                    data -> data.save(new CompoundTag(), NbtCompat.builtinRegistries())),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final List<BindingRecord> records = new ArrayList<>();
    private final Map<UUID, List<BindingRecord>> byPlayer = new HashMap<>();
    private final Map<Integer, List<BindingRecord>> byNetwork = new HashMap<>();
    private final Map<BindingKey, BindingRecord> byPosition = new HashMap<>();
    private final Map<BindingKey, List<BindingRecord>> byProvisionerEndpoint = new HashMap<>();

    public static BindingSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        BindingSavedData data = new BindingSavedData();
        ListTag list = tag.getListOrEmpty("bindings");
        for (int i = 0; i < list.size(); i++)
        {
            BindingRecord record = readRecord(list.getCompoundOrEmpty(i));
            if (record != null && (record.deviceType() == DeviceType.EXTERNAL_RECIPE_MACHINE
                    || record.deviceType() == DeviceType.PROVISIONER_RECIPE_BINDING)) data.addLoaded(record);
        }
        if (data.records.size() != list.size()) data.setDirty();
        return data;
    }

    public static BindingSavedData get(net.minecraft.server.MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<BindingRecord> records() { return List.copyOf(records); }
    public List<BindingRecord> forPlayer(UUID player) { return List.copyOf(byPlayer.getOrDefault(player, List.of())); }
    public List<BindingRecord> forNetwork(int networkId) { return List.copyOf(byNetwork.getOrDefault(networkId, List.of())); }
    public Set<Identifier> recipeTypesForProvisioner(ResourceKey<Level> dimension, BlockPos position)
    {
        HashSet<Identifier> result = new HashSet<>();
        provisionerRecords(dimension, position).forEach(record -> result.addAll(record.jeiRecipeTypes()));
        return Set.copyOf(result);
    }
    public Set<String> recipeFamiliesForProvisioner(ResourceKey<Level> dimension, BlockPos position)
    {
        HashSet<String> result = new HashSet<>();
        provisionerRecords(dimension, position).forEach(record -> result.addAll(record.recipeFamilies()));
        return Set.copyOf(result);
    }
    public Map<String, Set<String>> inputGroupsForProvisioner(ResourceKey<Level> dimension, BlockPos position)
    {
        HashMap<String, Set<String>> result = new HashMap<>();
        provisionerRecords(dimension, position).forEach(record ->
                record.provisionerInputGroups().forEach((family, groups) ->
                        result.merge(family, groups, (left, right) -> {
                            HashSet<String> merged = new HashSet<>(left);
                            merged.addAll(right);
                            return Set.copyOf(merged);
                        })));
        return Map.copyOf(result);
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
        NbtCompat.putUuid(entry, "id", record.id());
        NbtCompat.putUuid(entry, "owner", record.owner());
        entry.putInt("network", record.networkId());
        entry.putString("dimension", record.dimension().identifier().toString());
        entry.putInt("x", record.position().getX());
        entry.putInt("y", record.position().getY());
        entry.putInt("z", record.position().getZ());
        entry.putString("device", record.deviceType().name());
        entry.putString("block", record.lastBlockId().toString());
        if (record.provisionerDimension() != null && record.provisionerPosition() != null)
        {
            entry.putString("provisioner_dimension", record.provisionerDimension().identifier().toString());
            entry.putLong("provisioner_position", record.provisionerPosition().asLong());
        }
        entry.putString("name", record.nickname());
        entry.putBoolean("favorite", record.favorite());
        entry.putInt("priority", record.priority());
        entry.putLong("time", record.boundGameTime());
        ListTag families = new ListTag();
        record.recipeFamilies().forEach(family -> families.add(StringTag.valueOf(family)));
        entry.put("families", families);
        ListTag jeiTypes = new ListTag();
        record.jeiRecipeTypes().forEach(type -> jeiTypes.add(StringTag.valueOf(type.toString())));
        entry.put("jei_types", jeiTypes);
        ListTag inputGroups = new ListTag();
        record.provisionerInputGroups().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(groupEntry -> {
                    CompoundTag encoded = new CompoundTag();
                    encoded.putString("family", groupEntry.getKey());
                    ListTag groups = new ListTag();
                    groupEntry.getValue().stream().sorted()
                            .forEach(group -> groups.add(StringTag.valueOf(group)));
                    encoded.put("groups", groups);
                    inputGroups.add(encoded);
                });
        entry.put("provisioner_input_groups", inputGroups);
        return entry;
    }

    private static BindingRecord readRecord(CompoundTag entry)
    {
        try
        {
            Set<String> families = new HashSet<>();
            ListTag familyList = entry.getListOrEmpty("families");
            for (int i = 0; i < familyList.size(); i++) families.add(familyList.getStringOr(i, ""));
            Set<Identifier> jeiTypes = new HashSet<>();
            ListTag jeiTypeList = entry.getListOrEmpty("jei_types");
            for (int i = 0; i < jeiTypeList.size(); i++)
            {
                Identifier type = Identifier.tryParse(jeiTypeList.getStringOr(i, ""));
                if (type != null) jeiTypes.add(type);
            }
            Map<String, Set<String>> inputGroups = new HashMap<>();
            if (entry.contains("provisioner_input_groups"))
            {
                ListTag encodedGroups = entry.getListOrEmpty("provisioner_input_groups");
                for (int i = 0; i < encodedGroups.size(); i++)
                {
                    CompoundTag encoded = encodedGroups.getCompoundOrEmpty(i);
                    HashSet<String> groups = new HashSet<>();
                    ListTag values = encoded.getListOrEmpty("groups");
                    for (int j = 0; j < values.size(); j++) groups.add(values.getStringOr(j, ""));
                    inputGroups.put(encoded.getStringOr("family", ""), Set.copyOf(groups));
                }
            }
            else if (readDeviceType(entry.getStringOr("device", "EXTERNAL_RECIPE_MACHINE"))
                    == DeviceType.PROVISIONER_RECIPE_BINDING)
                families.forEach(family -> inputGroups.put(family, Set.of(BindingRecord.ALL_INPUT_GROUPS)));
            ResourceKey<Level> provisionerDimension = entry.contains("provisioner_dimension")
                    ? ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(entry.getStringOr("provisioner_dimension", ""))) : null;
            BlockPos provisionerPosition = entry.contains("provisioner_position")
                    ? BlockPos.of(entry.getLongOr("provisioner_position", 0L)) : null;
            return new BindingRecord(
                    NbtCompat.getUuid(entry, "id"), NbtCompat.getUuid(entry, "owner"), entry.getIntOr("network", -1),
                    ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            Identifier.parse(entry.getStringOr("dimension", "minecraft:overworld"))),
                    new BlockPos(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0)),
                    readDeviceType(entry.getStringOr("device", "EXTERNAL_RECIPE_MACHINE")), jeiTypes, families,
                    inputGroups,
                    Identifier.parse(entry.getStringOr("block", "minecraft:air")), provisionerDimension, provisionerPosition,
                    entry.getStringOr("name", ""),
                    entry.getBooleanOr("favorite", false), entry.getIntOr("priority", 0),
                    entry.getLongOr("time", 0L));
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
