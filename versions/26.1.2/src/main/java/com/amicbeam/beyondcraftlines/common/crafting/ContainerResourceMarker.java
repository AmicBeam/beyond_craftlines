package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.IStackHandlerWrapper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.StackHandlerWrapperHelper;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;

/** Reads a copied container's registered non-item capabilities without draining it. */
public final class ContainerResourceMarker
{
    private ContainerResourceMarker() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static IStackKey<?> contents(ItemStack source)
    {
        if (source.isEmpty()) return ItemStackKey.EMPTY;
        ItemStack stack = source.copy();
        for (var entry : CapabilityHelper.ItemCapabilityMap.entrySet().stream()
                .sorted(java.util.Comparator.comparing(value -> value.getKey().toString())).toList())
        {
            var factory = StackHandlerWrapperHelper.stackWrappers.get(entry.getKey());
            if (factory == null) continue;
            try
            {
                Object handler = net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack).getCapability(
                        (net.neoforged.neoforge.capabilities.ItemCapability) entry.getValue());
                if (handler == null) continue;
                IStackHandlerWrapper wrapper = (IStackHandlerWrapper) ((java.util.function.Function) factory).apply(handler);
                if (wrapper == null) continue;
                for (int slot = 0; slot < wrapper.getSlots(); slot++)
                {
                    var value = RecipeResourceResolver.fromStack(wrapper.getStackInSlot(slot));
                    if (value != null && !value.isEmpty() && !(value.key() instanceof ItemStackKey)) return value.key();
                }
            }
            catch (RuntimeException | LinkageError ignored) {}
        }
        return ItemStackKey.EMPTY;
    }

    public static IStackKey<?> mark(ItemStack source, boolean contents)
    {
        if (source.isEmpty()) return ItemStackKey.EMPTY;
        return contents ? contents(source) : new ItemStackKey(source.copyWithCount(1));
    }
}
