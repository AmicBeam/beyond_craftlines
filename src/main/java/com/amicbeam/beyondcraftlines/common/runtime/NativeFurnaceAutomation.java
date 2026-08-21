package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Sends order materials through the public storage exposed by BD network furnaces. */
public final class NativeFurnaceAutomation
{
    private NativeFurnaceAutomation() {}

    public static long insertCapacity(BaseNetFurnaceBlockEntity<?> furnace, ResourceLocation itemId, long limit)
    { return insertCapacity(furnace, key(itemId), limit); }

    public static long insertCapacity(BaseNetFurnaceBlockEntity<?> furnace, ItemStackKey key, long limit)
    {
        if (limit <= 0) return 0;
        if (key.getReadOnlyStack().isEmpty()) return 0;
        StackHandler input = furnace.getInputStorageSlots();
        long accepted = 0;
        for (int slot = 0; slot < input.getSlots() && accepted < limit; slot++)
        {
            long request = limit - accepted;
            accepted += request - input.insert(slot, key, request, true).amount();
        }
        return accepted;
    }

    public static long insertCapacity(BaseNetFurnaceBlockEntity<?> furnace, IStackKey<?> key, long limit)
    { return key instanceof ItemStackKey item ? insertCapacity(furnace, item, limit) : 0; }

    public static boolean containsAnyInput(BaseNetFurnaceBlockEntity<?> furnace,
                                           Set<ResourceLocation> itemIds)
    {
        if (itemIds.isEmpty()) return false;
        StackHandler[] targetStorages = {furnace.getInputStorageSlots(), furnace.getOutputStorageSlots(),
                furnace.getFuelStorageSlots(), furnace.getFuelReturnSlots()};
        for (StackHandler storage : targetStorages)
            for (KeyAmount value : storage.getStorage())
                if (value.key() instanceof ItemStackKey itemKey
                        && itemIds.contains(BuiltInRegistries.ITEM.getKey(itemKey.getSource()))) return true;
        return false;
    }

    public static long insert(BaseNetFurnaceBlockEntity<?> furnace, ResourceLocation itemId, long amount)
    { return insert(furnace, key(itemId), amount); }

    public static long insert(BaseNetFurnaceBlockEntity<?> furnace, ItemStackKey key, long amount)
    {
        if (amount <= 0) return 0;
        StackHandler input = furnace.getInputStorageSlots();
        long inserted = 0;
        for (int slot = 0; slot < input.getSlots() && inserted < amount; slot++)
        {
            long request = amount - inserted;
            inserted += request - input.insert(slot, key, request, false).amount();
        }
        return inserted;
    }

    public static long insert(BaseNetFurnaceBlockEntity<?> furnace, IStackKey<?> key, long amount)
    { return key instanceof ItemStackKey item ? insert(furnace, item, amount) : 0; }

    public static long countOutput(BaseNetFurnaceBlockEntity<?> furnace, ResourceLocation itemId)
    {
        return count(furnace.getOutputStorageSlots(), itemId);
    }

    public static long extractOutput(BaseNetFurnaceBlockEntity<?> furnace, ResourceLocation itemId, long amount)
    {
        if (amount <= 0) return 0;
        StackHandler output = furnace.getOutputStorageSlots();
        long extracted = 0;
        for (int slot = 0; slot < output.getSlots() && extracted < amount; slot++)
        {
            KeyAmount visible = output.getStackBySlot(slot);
            if (!matches(visible, itemId)) continue;
            KeyAmount result = output.extract(slot, amount - extracted, false);
            if (matches(result, itemId)) extracted += result.amount();
        }
        return extracted;
    }

    public static List<KeyAmount> extractOutputStacks(BaseNetFurnaceBlockEntity<?> furnace,
                                                      ResourceLocation itemId, long amount)
    {
        if (amount <= 0) return List.of();
        StackHandler output = furnace.getOutputStorageSlots();
        List<KeyAmount> values = new ArrayList<>();
        long extracted = 0;
        for (int slot = 0; slot < output.getSlots() && extracted < amount; slot++)
        {
            KeyAmount visible = output.getStackBySlot(slot);
            if (!matches(visible, itemId)) continue;
            KeyAmount result = output.extract(slot, amount - extracted, false);
            if (!result.isEmpty())
            {
                values.add(result);
                extracted += result.amount();
            }
        }
        return List.copyOf(values);
    }

    public static long restoreOutput(BaseNetFurnaceBlockEntity<?> furnace, ResourceLocation itemId, long amount)
    { return restoreOutput(furnace, key(itemId), amount); }

    public static long restoreOutput(BaseNetFurnaceBlockEntity<?> furnace, ItemStackKey key, long amount)
    {
        if (amount <= 0) return 0;
        StackHandler output = furnace.getOutputStorageSlots();
        long inserted = 0;
        for (int slot = 0; slot < output.getSlots() && inserted < amount; slot++)
        {
            long request = amount - inserted;
            inserted += request - output.insert(slot, key, request, false).amount();
        }
        return inserted;
    }

    private static long count(StackHandler storage, ResourceLocation itemId)
    {
        long total = 0;
        for (KeyAmount value : storage.getStorage()) if (matches(value, itemId))
            total = SaturatingLongMath.add(total, value.amount());
        return total;
    }

    private static boolean matches(KeyAmount value, ResourceLocation itemId)
    {
        return value.key() instanceof ItemStackKey itemKey
                && BuiltInRegistries.ITEM.getKey(itemKey.getSource()).equals(itemId);
    }

    private static ItemStackKey key(ResourceLocation itemId)
    {
        return new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
    }
}
