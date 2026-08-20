package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Generic sided item-capability adapter used by directly bound third-party machines. */
public final class BoundMachineAutomation
{
    private BoundMachineAutomation() {}

    public static boolean isAutomatable(ServerLevel level, BlockPos position)
    {
        return !handlers(level, position).isEmpty();
    }

    public static boolean containsAny(ServerLevel level, BlockPos position, Set<ResourceLocation> itemIds)
    {
        if (itemIds.isEmpty()) return false;
        for (IItemHandler handler : handlers(level, position))
            for (int slot = 0; slot < handler.getSlots(); slot++)
            {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && itemIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    return true;
            }
        return false;
    }

    public static long insertCapacity(ServerLevel level, BlockPos position, ResourceLocation itemId, long limit)
    {
        return insertCapacity(handlers(level, position), new ItemStack(BuiltInRegistries.ITEM.get(itemId)), limit);
    }

    public static long insertCapacity(ServerLevel level, BlockPos position, ItemStackKey key, long limit)
    { return insertCapacity(handlers(level, position), key.getReadOnlyStack(), limit); }

    static long insertCapacity(List<IItemHandler> handlers, ResourceLocation itemId, long limit)
    { return insertCapacity(handlers, new ItemStack(BuiltInRegistries.ITEM.get(itemId)), limit); }

    private static long insertCapacity(List<IItemHandler> handlers, ItemStack template, long limit)
    {
        if (limit <= 0) return 0;
        if (template.isEmpty()) return 0;
        long best = 0;
        for (IItemHandler handler : handlers)
        {
            long accepted = 0;
            for (int slot = 0; slot < handler.getSlots() && accepted < limit; slot++)
            {
                int request = (int) Math.min(Integer.MAX_VALUE, limit - accepted);
                ItemStack offered = template.copyWithCount(request);
                accepted += request - handler.insertItem(slot, offered, true).getCount();
            }
            best = Math.max(best, accepted);
        }
        return best;
    }

    public static long insert(ServerLevel level, BlockPos position, ResourceLocation itemId, long amount)
    {
        return insert(handlers(level, position), new ItemStack(BuiltInRegistries.ITEM.get(itemId)), amount);
    }

    public static long insert(ServerLevel level, BlockPos position, ItemStackKey key, long amount)
    { return insert(handlers(level, position), key.getReadOnlyStack(), amount); }

    static long insert(List<IItemHandler> handlers, ResourceLocation itemId, long amount)
    { return insert(handlers, new ItemStack(BuiltInRegistries.ITEM.get(itemId)), amount); }

    private static long insert(List<IItemHandler> handlers, ItemStack template, long amount)
    {
        if (amount <= 0) return 0;
        IItemHandler handler = handlers.stream()
                .max(java.util.Comparator.comparingLong(value -> simulatedInsert(value, template, amount)))
                .orElse(null);
        if (handler == null) return 0;
        long inserted = 0;
        for (int slot = 0; slot < handler.getSlots() && inserted < amount; slot++)
        {
            int request = (int) Math.min(Integer.MAX_VALUE, amount - inserted);
            ItemStack offered = template.copyWithCount(request);
            inserted += request - handler.insertItem(slot, offered, false).getCount();
        }
        return inserted;
    }

    public static long countExtractable(ServerLevel level, BlockPos position, ResourceLocation itemId)
    {
        return countExtractable(handlers(level, position), itemId);
    }

    static long countExtractable(List<IItemHandler> handlers, ResourceLocation itemId)
    {
        long best = 0;
        for (IItemHandler handler : handlers)
            best = Math.max(best, countExtractable(handler, itemId));
        return best;
    }

    public static long extract(ServerLevel level, BlockPos position, ResourceLocation itemId, long amount)
    {
        return extract(handlers(level, position), itemId, amount);
    }

    public static List<KeyAmount> extractStacks(ServerLevel level, BlockPos position,
                                                ResourceLocation itemId, long amount)
    {
        List<IItemHandler> handlers = handlers(level, position);
        IItemHandler handler = handlers.stream()
                .max(java.util.Comparator.comparingLong(value -> countExtractable(value, itemId)))
                .orElse(null);
        if (handler == null || amount <= 0) return List.of();
        List<KeyAmount> result = new ArrayList<>();
        long extracted = 0;
        for (int slot = 0; slot < handler.getSlots() && extracted < amount; slot++)
        {
            ItemStack visible = handler.getStackInSlot(slot);
            if (visible.isEmpty() || !BuiltInRegistries.ITEM.getKey(visible.getItem()).equals(itemId)) continue;
            int request = (int) Math.min(Integer.MAX_VALUE, amount - extracted);
            ItemStack taken = handler.extractItem(slot, request, false);
            if (taken.isEmpty()) continue;
            result.add(new KeyAmount(new ItemStackKey(taken), taken.getCount()));
            extracted += taken.getCount();
        }
        return List.copyOf(result);
    }

    static long extract(List<IItemHandler> handlers, ResourceLocation itemId, long amount)
    {
        IItemHandler handler = handlers.stream()
                .max(java.util.Comparator.comparingLong(value -> countExtractable(value, itemId)))
                .orElse(null);
        if (handler == null) return 0;
        long extracted = 0;
        for (int slot = 0; slot < handler.getSlots() && extracted < amount; slot++)
        {
            ItemStack visible = handler.getStackInSlot(slot);
            if (!visible.isEmpty() && BuiltInRegistries.ITEM.getKey(visible.getItem()).equals(itemId))
            {
                int request = (int) Math.min(Integer.MAX_VALUE, amount - extracted);
                ItemStack result = handler.extractItem(slot, request, false);
                if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(itemId))
                    extracted += result.getCount();
            }
        }
        return extracted;
    }

    private static long simulatedInsert(IItemHandler handler, ItemStack template, long amount)
    {
        long accepted = 0;
        for (int slot = 0; slot < handler.getSlots() && accepted < amount; slot++)
        {
            int request = (int) Math.min(Integer.MAX_VALUE, amount - accepted);
            accepted += request - handler.insertItem(slot, template.copyWithCount(request), true).getCount();
        }
        return accepted;
    }

    private static long countExtractable(IItemHandler handler, ResourceLocation itemId)
    {
        long total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            ItemStack visible = handler.getStackInSlot(slot);
            if (visible.isEmpty() || !BuiltInRegistries.ITEM.getKey(visible.getItem()).equals(itemId)) continue;
            ItemStack simulated = handler.extractItem(slot, visible.getCount(), true);
            if (!simulated.isEmpty() && BuiltInRegistries.ITEM.getKey(simulated.getItem()).equals(itemId))
                total += simulated.getCount();
        }
        return total;
    }

    private static List<IItemHandler> handlers(ServerLevel level, BlockPos position)
    {
        if (!level.isLoaded(position)) return List.of();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IItemHandler> result = new ArrayList<>();
        add(level.getCapability(Capabilities.ItemHandler.BLOCK, position, null), seen, result);
        for (Direction direction : Direction.values())
            add(level.getCapability(Capabilities.ItemHandler.BLOCK, position, direction), seen, result);
        return result;
    }

    private static void add(IItemHandler handler, Set<IItemHandler> seen, List<IItemHandler> result)
    {
        if (handler != null && handler.getSlots() > 0 && seen.add(handler)) result.add(handler);
    }
}
