package com.amicbeam.beyondcraftlines.compat;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;

/** Forge-only inspection kept out of the pure identity-token tests. */
public final class IngredientSelectionKeyCompat
{
    private IngredientSelectionKeyCompat() {}

    public static boolean hasDefaultIdentity(IStackKey<?> key)
    {
        if (!(key instanceof ItemStackKey itemKey)) return false;
        ItemStack stack = itemKey.getReadOnlyStack();
        if (stack.isEmpty()) return false;
        ItemStack baseline = new ItemStack(stack.getItem());
        return ItemStack.isSameItemSameTags(stack, baseline) && stack.areCapsCompatible(baseline);
    }
}
