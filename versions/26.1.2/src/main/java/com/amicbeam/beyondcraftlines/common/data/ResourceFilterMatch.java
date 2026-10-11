package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;

public final class ResourceFilterMatch
{
    private ResourceFilterMatch() {}

    public static boolean matches(IStackKey<?> left, IStackKey<?> right, boolean matchNbt)
    {
        if (!left.getTypeId().equals(right.getTypeId())) return false;
        if (left instanceof ItemStackKey a && right instanceof ItemStackKey b)
        {
            ItemStack sample = a.getReadOnlyStack();
            ItemStack candidate = b.getReadOnlyStack();
            if (!ItemStack.isSameItem(sample, candidate)) return false;
            if (!matchNbt) return true;
            sample = sample.copy();
            candidate = candidate.copy();
            sample.setDamageValue(0);
            candidate.setDamageValue(0);
            return ResourceFilterRules.matchesItemMetadata(
                    ItemStack.isSameItemSameComponents(sample, candidate), true);

        }
        // Sky Logistics applies the NBT/durability switches to item samples only.
        return StackKeyMatch.exact(left, right);
    }
}
