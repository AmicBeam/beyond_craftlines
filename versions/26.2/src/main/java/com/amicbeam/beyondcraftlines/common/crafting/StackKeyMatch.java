package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;

/** Symmetric exact matching for keys reconstructed by different protocol and capability paths. */
public final class StackKeyMatch
{
    private StackKeyMatch() {}

    public static boolean exact(IStackKey<?> left, IStackKey<?> right)
    {
        return SymmetricMatch.exactOrCanonical(left, right, IStackKey::isSameTypeSameComponents,
                StackKeyMatch::sameItemStackComponents);
    }

    private static boolean sameItemStackComponents(IStackKey<?> left, IStackKey<?> right)
    {
        return left instanceof ItemStackKey leftItem && right instanceof ItemStackKey rightItem
                && ItemStack.isSameItemSameComponents(
                leftItem.getReadOnlyStack(), rightItem.getReadOnlyStack());
    }
}
