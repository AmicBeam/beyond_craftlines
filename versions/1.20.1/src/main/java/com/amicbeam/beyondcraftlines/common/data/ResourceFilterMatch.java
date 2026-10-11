package com.amicbeam.beyondcraftlines.common.data;

import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;

/** Forge NBT counterpart of the component-based item filter matcher. */
public final class ResourceFilterMatch
{
    private ResourceFilterMatch() {}
    public static boolean matches(IStackKey<?> left, IStackKey<?> right, boolean matchNbt, boolean matchDurability)
    {
        if (!left.getTypeId().equals(right.getTypeId())) return false;
        if (left instanceof ItemStackKey a && right instanceof ItemStackKey b)
        {
            ItemStack sample = a.getReadOnlyStack(), candidate = b.getReadOnlyStack();
            if (!ItemStack.isSameItem(sample, candidate)) return false;
            boolean sameNbt = !matchNbt || Objects.equals(comparableTag(sample, matchDurability),
                    comparableTag(candidate, matchDurability));
            return ResourceFilterRules.matchesItemMetadata(sameNbt, sample.isDamageableItem(),
                    sample.getDamageValue() == candidate.getDamageValue(), matchNbt, matchDurability);
        }
        return StackKeyMatch.exact(left, right);
    }
    private static CompoundTag comparableTag(ItemStack stack, boolean matchDurability)
    {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        CompoundTag copy = tag.copy();
        if (!matchDurability) copy.remove("Damage");
        return copy.isEmpty() ? null : copy;
    }
}
