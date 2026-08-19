package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AnchorSelectionSavedData extends SavedData
{
    private static final String NAME = "beyond_craftlines_anchor_selections";
    private final Map<UUID, Selection> selections = new HashMap<>();

    public static AnchorSelectionSavedData get(net.minecraft.server.MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(AnchorSelectionSavedData::new, AnchorSelectionSavedData::load), NAME);
    }

    public static AnchorSelectionSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        AnchorSelectionSavedData data = new AnchorSelectionSavedData();
        ListTag list = tag.getList("selections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            UUID player = entry.getUUID("player");
            BlockPos first = readPos(entry, "first");
            BlockPos second = entry.contains("second", Tag.TAG_COMPOUND) ? readPos(entry, "second") : null;
            data.selections.put(player, new Selection(first, second));
        }
        return data;
    }

    public Selection get(UUID player) { return selections.get(player); }

    public void set(UUID player, Selection selection)
    {
        selections.put(player, selection);
        setDirty();
    }

    public void clear(UUID player)
    {
        if (selections.remove(player) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        selections.forEach((player, selection) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            writePos(entry, "first", selection.first());
            if (selection.second() != null) writePos(entry, "second", selection.second());
            list.add(entry);
        });
        tag.put("selections", list);
        return tag;
    }

    private static BlockPos readPos(CompoundTag parent, String key)
    {
        CompoundTag pos = parent.getCompound(key);
        return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
    }

    private static void writePos(CompoundTag parent, String key, BlockPos value)
    {
        CompoundTag pos = new CompoundTag();
        pos.putInt("x", value.getX()); pos.putInt("y", value.getY()); pos.putInt("z", value.getZ());
        parent.put(key, pos);
    }

    public record Selection(BlockPos first, BlockPos second) {}
}
