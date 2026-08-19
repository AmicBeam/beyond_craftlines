package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BlueprintLibrarySavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_blueprints";
    private final Map<UUID, BlueprintRecord> records = new HashMap<>();

    public static BlueprintLibrarySavedData get(net.minecraft.server.MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(BlueprintLibrarySavedData::new, BlueprintLibrarySavedData::load), NAME);
    }

    public static BlueprintLibrarySavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        BlueprintLibrarySavedData data = new BlueprintLibrarySavedData();
        ListTag list = tag.getList("blueprints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            try
            {
                StructureSnapshot snapshot = readSnapshot(entry.getCompound("snapshot"));
                data.records.put(entry.getUUID("id"), new BlueprintRecord(entry.getUUID("id"),
                        entry.getUUID("owner"), entry.getString("name"), snapshot,
                        BlueprintRecord.State.valueOf(entry.getString("state")),
                        entry.contains("compiled", Tag.TAG_COMPOUND) ? readCompiled(entry.getCompound("compiled")) : null));
            }
            catch (RuntimeException ignored) {}
        }
        return data;
    }

    public Optional<BlueprintRecord> get(UUID id) { return Optional.ofNullable(records.get(id)); }
    public Optional<BlueprintRecord> getByResourceId(ResourceLocation id)
    {
        if (!"beyond_craftlines".equals(id.getNamespace())) return Optional.empty();
        try { return get(UUID.fromString(id.getPath())); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
    public List<BlueprintRecord> all() { return List.copyOf(records.values()); }

    public Optional<BlueprintRecord> findCompiledByHash(String hash)
    {
        return records.values().stream()
                .filter(record -> record.compiled() != null && record.compiled().structureHash().equals(hash))
                .findFirst();
    }

    public Optional<BlueprintRecord> compile(UUID id, UUID actor)
    {
        BlueprintRecord record = records.get(id);
        if (record == null || !record.owner().equals(actor)
                || record.snapshot().blocks().isEmpty()
                || record.snapshot().hash().isBlank()) return Optional.empty();
        List<ResourceAmount> capex = record.snapshot().itemTotals().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new ResourceAmount(ResourceLocation.parse(entry.getKey()), entry.getValue()))
                .toList();
        CompiledBlueprint compiledData = new CompiledBlueprint(record.id(), record.owner(), record.snapshot().hash(),
                capex,
                List.of(),
                List.of(),
                0, Math.max(20, record.snapshot().blocks().size()), 1, 1);
        BlueprintRecord compiled = new BlueprintRecord(record.id(), record.owner(), record.name(), record.snapshot(), BlueprintRecord.State.COMPILED, compiledData);
        put(compiled);
        return Optional.of(compiled);
    }

    public void put(BlueprintRecord record)
    {
        records.put(record.id(), record);
        setDirty();
    }

    public BlueprintRecord capture(Level level, BlockPos min, BlockPos max, UUID owner, String name)
    {
        BlockPos low = new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()));
        BlockPos high = new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
        List<StructureSnapshot.BlockEntry> blocks = new ArrayList<>();
        Map<String, Integer> itemTotals = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(low, high))
        {
            BlockState state = level.getBlockState(pos);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            net.minecraft.nbt.CompoundTag blockEntityData = null;
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null)
            {
                blockEntityData = blockEntity.saveWithFullMetadata(level.registryAccess());
                blockEntityData.remove("x");
                blockEntityData.remove("y");
                blockEntityData.remove("z");
            }
            blocks.add(new StructureSnapshot.BlockEntry(pos.subtract(low), id, state.toString(), blockEntityData));
            if (state.getBlock().asItem() != net.minecraft.world.item.Items.AIR)
            {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(state.getBlock().asItem());
                itemTotals.merge(itemId.toString(), 1, Integer::sum);
            }
        }
        BlockPos size = high.subtract(low).offset(1, 1, 1);
        StructureSnapshot snapshot = new StructureSnapshot(size, blocks, itemTotals, hash(blocks));
        BlueprintRecord record = new BlueprintRecord(UUID.randomUUID(), owner, name, snapshot, BlueprintRecord.State.DRAFT);
        put(record);
        return record;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (BlueprintRecord record : records.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", record.id());
            entry.putUUID("owner", record.owner());
            entry.putString("name", record.name());
            entry.putString("state", record.state().name());
            entry.put("snapshot", writeSnapshot(record.snapshot()));
            if (record.compiled() != null) entry.put("compiled", writeCompiled(record.compiled()));
            list.add(entry);
        }
        tag.put("blueprints", list);
        return tag;
    }

    private static CompoundTag writeSnapshot(StructureSnapshot snapshot)
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("sx", snapshot.size().getX()); tag.putInt("sy", snapshot.size().getY()); tag.putInt("sz", snapshot.size().getZ());
        tag.putString("hash", snapshot.hash());
        CompoundTag totals = new CompoundTag();
        for (Map.Entry<String, Integer> entry : snapshot.itemTotals().entrySet())
        {
            totals.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("item_totals", totals);
        ListTag blocks = new ListTag();
        for (StructureSnapshot.BlockEntry block : snapshot.blocks())
        {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", block.relativePos().getX()); entry.putInt("y", block.relativePos().getY()); entry.putInt("z", block.relativePos().getZ());
            entry.putString("id", block.blockId().toString()); entry.putString("state", block.state());
            if (block.blockEntityData() != null) entry.put("block_entity", block.blockEntityData().copy());
            blocks.add(entry);
        }
        tag.put("blocks", blocks);
        return tag;
    }

    private static StructureSnapshot readSnapshot(CompoundTag tag)
    {
        List<StructureSnapshot.BlockEntry> blocks = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            blocks.add(new StructureSnapshot.BlockEntry(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")),
                    ResourceLocation.parse(entry.getString("id")), entry.getString("state"),
                    entry.contains("block_entity", Tag.TAG_COMPOUND)
                            ? entry.getCompound("block_entity").copy() : null));
        }
        Map<String, Integer> itemTotals = new HashMap<>();
        CompoundTag totals = tag.getCompound("item_totals");
        for (String key : totals.getAllKeys()) itemTotals.put(key, totals.getInt(key));
        String savedHash = tag.getString("hash");
        String actualHash = hash(blocks);
        return new StructureSnapshot(new BlockPos(tag.getInt("sx"), tag.getInt("sy"), tag.getInt("sz")),
                blocks, itemTotals, savedHash.isBlank() ? actualHash : savedHash);
    }

    private static CompoundTag writeCompiled(CompiledBlueprint compiled)
    {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", compiled.id());
        tag.putUUID("owner", compiled.owner());
        tag.putString("structure_hash", compiled.structureHash());
        tag.put("capex", writeAmounts(compiled.capex()));
        tag.put("inputs", writeAmounts(compiled.inputs()));
        tag.put("outputs", writeAmounts(compiled.outputs()));
        tag.put("fluid_inputs", writeFluids(compiled.fluidInputs()));
        tag.put("fluid_outputs", writeFluids(compiled.fluidOutputs()));
        tag.putLong("energy_net", compiled.energyNet());
        tag.putLong("cycle_ticks", compiled.cycleTicks());
        tag.putInt("version", compiled.version());
        tag.putInt("schema_version", compiled.schemaVersion());
        return tag;
    }

    private static ListTag writeAmounts(List<ResourceAmount> amounts)
    {
        ListTag list = new ListTag();
        for (ResourceAmount amount : amounts)
        {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", amount.itemId().toString());
            entry.putLong("amount", amount.amount());
            list.add(entry);
        }
        return list;
    }

    private static CompiledBlueprint readCompiled(CompoundTag tag)
    {
        return new CompiledBlueprint(
                tag.getUUID("id"),
                tag.getUUID("owner"),
                tag.getString("structure_hash"),
                readAmounts(tag.getList("capex", Tag.TAG_COMPOUND)),
                readAmounts(tag.getList("inputs", Tag.TAG_COMPOUND)),
                readAmounts(tag.getList("outputs", Tag.TAG_COMPOUND)),
                readFluids(tag.getList("fluid_inputs", Tag.TAG_COMPOUND)),
                readFluids(tag.getList("fluid_outputs", Tag.TAG_COMPOUND)),
                tag.getLong("energy_net"),
                tag.getLong("cycle_ticks"),
                tag.getInt("version"),
                tag.getInt("schema_version"));
    }

    private static ListTag writeFluids(List<FluidAmount> amounts)
    {
        ListTag list = new ListTag();
        for (FluidAmount amount : amounts)
        {
            CompoundTag entry = new CompoundTag();
            entry.putString("fluid", amount.fluidId().toString());
            entry.putLong("amount", amount.amount());
            list.add(entry);
        }
        return list;
    }

    private static List<FluidAmount> readFluids(ListTag list)
    {
        List<FluidAmount> amounts = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            amounts.add(new FluidAmount(ResourceLocation.parse(entry.getString("fluid")), entry.getLong("amount")));
        }
        return amounts;
    }

    private static List<ResourceAmount> readAmounts(ListTag list)
    {
        List<ResourceAmount> amounts = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            amounts.add(new ResourceAmount(ResourceLocation.parse(entry.getString("item")), entry.getLong("amount")));
        }
        return amounts;
    }

    private static String hash(List<StructureSnapshot.BlockEntry> blocks)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            blocks.stream().map(block -> block.relativePos() + "|" + block.blockId() + "|" + block.state()
                            + "|" + (block.blockEntityData() == null ? "" : block.blockEntityData().toString()))
                    .sorted().forEach(value -> digest.update(value.getBytes(StandardCharsets.UTF_8)));
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02x", value));
            return result.toString();
        }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
