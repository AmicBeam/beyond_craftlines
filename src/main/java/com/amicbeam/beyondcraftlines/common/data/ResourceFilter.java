package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Fixed ghost slots; an empty blacklist preserves the behavior of older saves. */
public record ResourceFilter(boolean whitelist, List<IStackKey<?>> entries)
{
    public static final int COLUMNS = ResourceFilterRules.COLUMNS;
    public static final int ROWS = ResourceFilterRules.ROWS;
    public static final int SIZE = COLUMNS * ROWS;
    public static final ResourceFilter EMPTY = new ResourceFilter(false, List.of());

    public ResourceFilter
    {
        entries = ResourceFilterRules.normalize(entries, ItemStackKey.EMPTY);
    }

    public boolean accepts(IStackKey<?> key)
    {
        return ResourceFilterRules.accepts(whitelist, entries, key, IStackKey::isEmpty, StackKeyMatch::exact);
    }

    public ResourceFilter withEntry(int slot, IStackKey<?> key)
    {
        ArrayList<IStackKey<?>> changed = new ArrayList<>(entries);
        changed.set(slot, key == null ? ItemStackKey.EMPTY : key);
        return new ResourceFilter(whitelist, changed);
    }

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(whitelist);
        for (IStackKey<?> entry : entries) IStackKey.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, entry);
    }

    public static ResourceFilter read(FriendlyByteBuf buffer)
    {
        boolean whitelist = buffer.readBoolean();
        ArrayList<IStackKey<?>> entries = new ArrayList<>();
        for (int slot = 0; slot < SIZE; slot++) entries.add(IStackKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer));
        return new ResourceFilter(whitelist, entries);
    }

    public CompoundTag save(HolderLookup.Provider registries)
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("whitelist", whitelist);
        ListTag list = new ListTag();
        for (int slot = 0; slot < SIZE; slot++)
        {
            IStackKey<?> key = entries.get(slot);
            if (key.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot);
            entry.putString("type", key.getTypeId().toString());
            entry.put("key", key.serializeNBT(registries));
            list.add(entry);
        }
        tag.put("entries", list);
        return tag;
    }

    public static ResourceFilter load(CompoundTag tag, HolderLookup.Provider registries)
    {
        ResourceFilter filter = new ResourceFilter(tag.getBoolean("whitelist"), List.of());
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(SIZE, list.size()); index++)
        {
            CompoundTag entry = list.getCompound(index);
            int slot = entry.getInt("slot");
            ResourceLocation type = ResourceLocation.tryParse(entry.getString("type"));
            if (slot < 0 || slot >= SIZE || type == null) continue;
            try { filter = filter.withEntry(slot, StackKeyRegistry.getType(type)
                    .deserializeNBT(entry.getCompound("key"), registries)); }
            catch (RuntimeException | LinkageError ignored) {}
        }
        return filter;
    }
}
