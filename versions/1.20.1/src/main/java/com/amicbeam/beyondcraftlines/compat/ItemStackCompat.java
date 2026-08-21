package com.amicbeam.beyondcraftlines.compat;

import net.minecraft.world.item.ItemStack;

public final class ItemStackCompat {
    private ItemStackCompat() {}
    public static ItemStack copy(ItemStack stack, long count) {
        ItemStack result = stack.copy();
        result.setCount((int) Math.max(0, Math.min(Integer.MAX_VALUE, count)));
        return result;
    }
}
