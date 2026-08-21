package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.NetFurnaceBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class NativeFurnaceAutomation {
    private NativeFurnaceAutomation() {}
    public static long insertCapacity(NetFurnaceBlockEntity furnace, ResourceLocation itemId, long limit) { return insertCapacity(furnace, key(itemId), limit); }
    public static long insertCapacity(NetFurnaceBlockEntity furnace, ItemStackKey key, long limit) { return transferInto(furnace.getInputStorageSlots(), key, limit, true); }
    public static long insertCapacity(NetFurnaceBlockEntity furnace, IStackKey<?> key, long limit) { return key instanceof ItemStackKey item ? insertCapacity(furnace, item, limit) : 0; }
    public static long insert(NetFurnaceBlockEntity furnace, ResourceLocation itemId, long amount) { return insert(furnace, key(itemId), amount); }
    public static long insert(NetFurnaceBlockEntity furnace, ItemStackKey key, long amount) { return transferInto(furnace.getInputStorageSlots(), key, amount, false); }
    public static long insert(NetFurnaceBlockEntity furnace, IStackKey<?> key, long amount) { return key instanceof ItemStackKey item ? insert(furnace, item, amount) : 0; }
    public static boolean containsAnyInput(NetFurnaceBlockEntity furnace, Set<ResourceLocation> itemIds) {
        if (itemIds.isEmpty()) return false;
        for (StackHandler storage : new StackHandler[] { furnace.getInputStorageSlots(), furnace.getOutputStorageSlots(), furnace.getFuelStorageSlots(), furnace.getFuelReturnSlots() })
            for (KeyAmount value : storage.getStorage()) if (matches(value, itemIds)) return true;
        return false;
    }
    public static long countOutput(NetFurnaceBlockEntity furnace, ResourceLocation itemId) {
        long total = 0; for (KeyAmount value : furnace.getOutputStorageSlots().getStorage()) if (matches(value, itemId)) total = SaturatingLongMath.add(total, value.amount()); return total;
    }
    public static long extractOutput(NetFurnaceBlockEntity furnace, ResourceLocation itemId, long amount) {
        return extractOutputStacks(furnace, itemId, amount).stream().mapToLong(KeyAmount::amount).sum();
    }
    public static List<KeyAmount> extractOutputStacks(NetFurnaceBlockEntity furnace, ResourceLocation itemId, long amount) {
        if (amount <= 0) return List.of();
        StackHandler output = furnace.getOutputStorageSlots(); List<KeyAmount> result = new ArrayList<>(); long extracted = 0;
        for (int slot = 0; slot < output.getSlots() && extracted < amount; slot++) {
            if (!matches(output.getStackBySlot(slot), itemId)) continue;
            KeyAmount value = output.extract(slot, amount - extracted, false);
            if (!value.isEmpty()) { result.add(value); extracted += value.amount(); }
        }
        return List.copyOf(result);
    }
    public static long restoreOutput(NetFurnaceBlockEntity furnace, ResourceLocation itemId, long amount) { return restoreOutput(furnace, key(itemId), amount); }
    public static long restoreOutput(NetFurnaceBlockEntity furnace, ItemStackKey key, long amount) { return transferInto(furnace.getOutputStorageSlots(), key, amount, false); }

    private static long transferInto(StackHandler storage, ItemStackKey key, long amount, boolean simulate) {
        if (amount <= 0 || key.getReadOnlyStack().isEmpty()) return 0; long inserted = 0;
        for (int slot = 0; slot < storage.getSlots() && inserted < amount; slot++) { long request = amount - inserted; inserted += request - storage.insert(slot, key, request, simulate).amount(); }
        return inserted;
    }
    private static boolean matches(KeyAmount value, ResourceLocation id) { return value.key() instanceof ItemStackKey key && BuiltInRegistries.ITEM.getKey(key.getSource()).equals(id); }
    private static boolean matches(KeyAmount value, Set<ResourceLocation> ids) { return value.key() instanceof ItemStackKey key && ids.contains(BuiltInRegistries.ITEM.getKey(key.getSource())); }
    private static ItemStackKey key(ResourceLocation id) { return new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(id))); }
}
