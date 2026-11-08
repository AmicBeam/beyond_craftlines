package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Packs actual component-distinct outputs into a copy before any inventory insertion. */
final class ActualOutputCapacity
{
    private ActualOutputCapacity() {}
    static boolean fits(List<ItemStack> contents, List<KeyAmount> outputs)
    {
        List<StackAmount> stacks = new ArrayList<>();
        for (KeyAmount output : outputs)
        {
            if (!(output.key() instanceof ItemStackKey item)) return false;
            stacks.add(new StackAmount(item.getReadOnlyStack(), output.amount()));
        }
        return fitsStacks(contents, stacks);
    }
    record StackAmount(ItemStack stack, long amount) {}
    static boolean fitsStacks(List<ItemStack> contents, List<StackAmount> outputs)
    {
        List<ItemStack> slots = new ArrayList<>(contents.stream().map(ItemStack::copy).toList());
        for (StackAmount output : outputs)
        {
            ItemStack template = output.stack();
            long remaining = output.amount();
            for (ItemStack slot : slots)
            {
                if (remaining == 0) break;
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, template)) continue;
                int moved = (int) Math.min(remaining, Math.max(0, slot.getMaxStackSize() - slot.getCount()));
                slot.grow(moved); remaining -= moved;
            }
            for (int i = 0; i < slots.size() && remaining > 0; i++)
            {
                if (!slots.get(i).isEmpty()) continue;
                int moved = (int) Math.min(remaining, template.getMaxStackSize());
                ItemStack stack = template.copy(); stack.setCount(moved); slots.set(i, stack); remaining -= moved;
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}
