package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.IStackHandlerWrapper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.StackHandlerWrapperHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Generic sided item-capability adapter used by directly bound recipe machines. */
public final class BoundMachineAutomation
{
    private BoundMachineAutomation() {}

    public static boolean isAutomatable(ServerLevel level, BlockPos position)
    {
        return !resourceHandlers(level, position).isEmpty();
    }

    public static boolean containsAny(ServerLevel level, BlockPos position, Set<Identifier> itemIds)
    {
        if (itemIds.isEmpty()) return false;
        for (ItemHandler handler : handlers(level, position))
            for (int slot = 0; slot < handler.getSlots(); slot++)
            {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && itemIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    return true;
            }
        return false;
    }

    /** Checks every native BD resource capability, including fluids and optional chemicals. */
    public static boolean containsAnyResources(ServerLevel level, BlockPos position,
                                               List<RecipePlan.Material> materials)
    {
        if (materials.isEmpty()) return false;
        for (BdResourceHandler handler : resourceHandlers(level, position))
        {
            for (int slot = 0; slot < handler.wrapper().getSlots(); slot++)
            {
                KeyAmount present = RecipeResourceResolver.fromStack(handler.wrapper().getStackInSlot(slot));
                if (present == null || present.isEmpty()) continue;
                for (RecipePlan.Material material : materials)
                    if (material.key().isSame(present.key())) return true;
            }
        }
        return false;
    }

    public static long insertCapacity(ServerLevel level, BlockPos position, Identifier itemId, long limit)
    {
        return insertCapacity(handlers(level, position), new ItemStack(BuiltInRegistries.ITEM.getValue(itemId)), limit);
    }

    public static long insertCapacity(ServerLevel level, BlockPos position, ItemStackKey key, long limit)
    { return insertCapacity(handlers(level, position), key.getReadOnlyStack(), limit); }

    public static long insertCapacity(ServerLevel level, BlockPos position, IStackKey<?> key, long limit)
    {
        if (key instanceof ItemStackKey item) return insertCapacity(level, position, item, limit);
        long best = 0;
        for (BdResourceHandler handler : resourceHandlers(level, position))
            if (handler.type().equals(key.getTypeId()))
                best = Math.max(best, handler.insert(key, limit, true));
        return best;
    }

    static long insertCapacity(List<ItemHandler> handlers, Identifier itemId, long limit)
    { return insertCapacity(handlers, new ItemStack(BuiltInRegistries.ITEM.getValue(itemId)), limit); }

    private static long insertCapacity(List<ItemHandler> handlers, ItemStack template, long limit)
    {
        if (limit <= 0) return 0;
        if (template.isEmpty()) return 0;
        long best = 0;
        for (ItemHandler handler : handlers)
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

    public static long insert(ServerLevel level, BlockPos position, Identifier itemId, long amount)
    {
        return insert(handlers(level, position), new ItemStack(BuiltInRegistries.ITEM.getValue(itemId)), amount);
    }

    public static long insert(ServerLevel level, BlockPos position, ItemStackKey key, long amount)
    { return insert(handlers(level, position), key.getReadOnlyStack(), amount); }

    public static long insert(ServerLevel level, BlockPos position, IStackKey<?> key, long amount)
    {
        if (key instanceof ItemStackKey item) return insert(level, position, item, amount);
        BdResourceHandler selected = resourceHandlers(level, position).stream()
                .filter(handler -> handler.type().equals(key.getTypeId()))
                .max(java.util.Comparator.comparingLong(handler -> handler.insert(key, amount, true)))
                .orElse(null);
        return selected == null ? 0 : selected.insert(key, amount, false);
    }

    static long insert(List<ItemHandler> handlers, Identifier itemId, long amount)
    { return insert(handlers, new ItemStack(BuiltInRegistries.ITEM.getValue(itemId)), amount); }

    private static long insert(List<ItemHandler> handlers, ItemStack template, long amount)
    {
        if (amount <= 0) return 0;
        ItemHandler handler = handlers.stream()
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

    public static long countExtractable(ServerLevel level, BlockPos position, Identifier itemId)
    {
        return countExtractable(handlers(level, position), itemId);
    }

    public static long countExtractable(ServerLevel level, BlockPos position, IStackKey<?> key)
    {
        long best = 0;
        for (BdResourceHandler handler : resourceHandlers(level, position))
            if (handler.type().equals(key.getTypeId())) best = Math.max(best, handler.count(key));
        return best;
    }

    public static List<KeyAmount> extractStacks(ServerLevel level, BlockPos position,
                                                IStackKey<?> key, long amount)
    {
        BdResourceHandler selected = resourceHandlers(level, position).stream()
                .filter(handler -> handler.type().equals(key.getTypeId()))
                .max(java.util.Comparator.comparingLong(handler -> handler.count(key))).orElse(null);
        return selected == null ? List.of() : selected.extract(key, amount);
    }

    static long countExtractable(List<ItemHandler> handlers, Identifier itemId)
    {
        long best = 0;
        for (ItemHandler handler : handlers)
            best = Math.max(best, countExtractable(handler, itemId));
        return best;
    }

    public static long extract(ServerLevel level, BlockPos position, Identifier itemId, long amount)
    {
        return extract(handlers(level, position), itemId, amount);
    }

    public static List<KeyAmount> extractStacks(ServerLevel level, BlockPos position,
                                                Identifier itemId, long amount)
    {
        List<ItemHandler> handlers = handlers(level, position);
        ItemHandler handler = handlers.stream()
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

    static long extract(List<ItemHandler> handlers, Identifier itemId, long amount)
    {
        ItemHandler handler = handlers.stream()
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

    private static long simulatedInsert(ItemHandler handler, ItemStack template, long amount)
    {
        long accepted = 0;
        for (int slot = 0; slot < handler.getSlots() && accepted < amount; slot++)
        {
            int request = (int) Math.min(Integer.MAX_VALUE, amount - accepted);
            accepted += request - handler.insertItem(slot, template.copyWithCount(request), true).getCount();
        }
        return accepted;
    }

    private static long countExtractable(ItemHandler handler, Identifier itemId)
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

    private static List<ItemHandler> handlers(ServerLevel level, BlockPos position)
    {
        if (!level.isLoaded(position)) return List.of();
        Set<ResourceHandler<ItemResource>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemHandler> result = new ArrayList<>();
        add(level.getCapability(Capabilities.Item.BLOCK, position, null), seen, result);
        for (Direction direction : Direction.values())
            add(level.getCapability(Capabilities.Item.BLOCK, position, direction), seen, result);
        return result;
    }

    private static void add(ResourceHandler<ItemResource> handler, Set<ResourceHandler<ItemResource>> seen,
                            List<ItemHandler> result)
    {
        if (handler != null && handler.size() > 0 && seen.add(handler)) result.add(new ItemHandlerAdapter(handler));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<BdResourceHandler> resourceHandlers(ServerLevel level, BlockPos position)
    {
        if (!level.isLoaded(position)) return List.of();
        List<BdResourceHandler> result = new ArrayList<>();
        CapabilityHelper.BlockCapabilityMap.forEach((type, capability) -> {
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            var factory = StackHandlerWrapperHelper.stackWrappers.get(type);
            if (factory == null) return;
            for (Direction side : allSides())
            {
                try
                {
                    Object raw = level.getCapability((net.neoforged.neoforge.capabilities.BlockCapability) capability,
                            position, side);
                    if (raw == null || !seen.add(raw)) continue;
                    IStackHandlerWrapper wrapper = (IStackHandlerWrapper) ((java.util.function.Function) factory)
                            .apply(raw);
                    if (wrapper != null && wrapper.getSlots() > 0)
                        result.add(new BdResourceHandler(type, wrapper));
                }
                catch (RuntimeException | LinkageError ignored) {}
            }
        });
        return List.copyOf(result);
    }

    private static List<Direction> allSides()
    {
        List<Direction> result = new ArrayList<>();
        result.add(null);
        result.addAll(List.of(Direction.values()));
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private interface ItemHandler
    {
        int getSlots();
        ItemStack getStackInSlot(int slot);
        ItemStack insertItem(int slot, ItemStack stack, boolean simulate);
        ItemStack extractItem(int slot, int amount, boolean simulate);
    }

    private record ItemHandlerAdapter(ResourceHandler<ItemResource> handler) implements ItemHandler
    {
        @Override public int getSlots() { return handler.size(); }
        @Override public ItemStack getStackInSlot(int slot)
        {
            ItemResource resource = handler.getResource(slot);
            return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(slot));
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
        {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            try (Transaction transaction = Transaction.openRoot())
            {
                int inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
                if (!simulate) transaction.commit();
                return inserted >= stack.getCount() ? ItemStack.EMPTY
                        : stack.copyWithCount(stack.getCount() - inserted);
            }
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate)
        {
            if (amount <= 0) return ItemStack.EMPTY;
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) return ItemStack.EMPTY;
            try (Transaction transaction = Transaction.openRoot())
            {
                int extracted = handler.extract(slot, resource, amount, transaction);
                if (!simulate) transaction.commit();
                return resource.toStack(extracted);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private record BdResourceHandler(Identifier type, IStackHandlerWrapper wrapper)
    {
        long insert(IStackKey<?> key, long amount, boolean simulate)
        {
            if (amount <= 0) return 0;
            try
            {
                Object stack = key.copyStackWithCount(amount);
                long remaining = wrapper.insert(stack, simulate);
                return Math.max(0, amount - Math.max(0, remaining));
            }
            catch (RuntimeException | LinkageError ignored) { return 0; }
        }

        long count(IStackKey<?> key)
        {
            long result = 0;
            for (int slot = 0; slot < wrapper.getSlots(); slot++)
            {
                KeyAmount present = RecipeResourceResolver.fromStack(wrapper.getStackInSlot(slot));
                if (present != null && key.isSame(present.key()))
                {
                    long extracted = wrapper.extract(slot, present.amount(), true);
                    result = result > Long.MAX_VALUE - extracted ? Long.MAX_VALUE : result + extracted;
                }
            }
            return result;
        }

        List<KeyAmount> extract(IStackKey<?> key, long amount)
        {
            if (amount <= 0) return List.of();
            List<KeyAmount> result = new ArrayList<>();
            long remaining = amount;
            for (int slot = 0; slot < wrapper.getSlots() && remaining > 0; slot++)
            {
                KeyAmount present = RecipeResourceResolver.fromStack(wrapper.getStackInSlot(slot));
                if (present == null || !key.isSame(present.key())) continue;
                long extracted = wrapper.extract(slot, Math.min(remaining, present.amount()), false);
                if (extracted > 0)
                {
                    result.add(new KeyAmount(present.key(), extracted));
                    remaining -= extracted;
                }
            }
            return List.copyOf(result);
        }
    }
}
