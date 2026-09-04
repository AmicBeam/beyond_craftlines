package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

/** Per-slot consumption semantics carried by JEI virtual recipes. */
public record VirtualInputUse(Kind kind, int damagePerCraft)
{
    public enum Kind { CONSUMED, REUSABLE, DURABILITY }

    public static final VirtualInputUse CONSUMED = new VirtualInputUse(Kind.CONSUMED, 0);
    public static final VirtualInputUse REUSABLE = new VirtualInputUse(Kind.REUSABLE, 0);

    public VirtualInputUse
    {
        if (kind == null || damagePerCraft < 0
                || kind == Kind.DURABILITY && damagePerCraft < 1
                || kind != Kind.DURABILITY && damagePerCraft != 0)
            throw new IllegalArgumentException("invalid virtual input use");
    }

    public static VirtualInputUse durability(int damagePerCraft)
    { return new VirtualInputUse(Kind.DURABILITY, damagePerCraft); }

    public boolean sharedReusable() { return kind == Kind.REUSABLE; }

    static VirtualInputUse fromRemainder(boolean sameItem, boolean sameComponents,
                                         boolean damageable, int damageBefore, int damageAfter)
    {
        if (!sameItem) return CONSUMED;
        if (sameComponents) return REUSABLE;
        return damageable && damageAfter > damageBefore
                ? durability(damageAfter - damageBefore) : CONSUMED;
    }

    static boolean matchesDurabilityVariant(IStackKey<?> expected, IStackKey<?> candidate)
    {
        if (!(expected instanceof ItemStackKey expectedItem)
                || !(candidate instanceof ItemStackKey candidateItem)) return false;
        return matchesDurabilityVariant(expectedItem.getReadOnlyStack(), candidateItem.getReadOnlyStack());
    }

    static boolean matchesDurabilityVariant(net.minecraft.world.item.ItemStack expected,
                                            net.minecraft.world.item.ItemStack candidate)
    {
        var left = expected.copy();
        var right = candidate.copy();
        if (!left.isDamageableItem() || !right.isDamageableItem()) return false;
        left.setDamageValue(0);
        right.setDamageValue(0);
        return net.minecraft.world.item.ItemStack.isSameItemSameComponents(left, right);
    }

    public long requiredAmount(long crafts, IStackKey<?> key, long amountPerCraft)
    {
        if (crafts < 1 || amountPerCraft < 1) throw new IllegalArgumentException("invalid input amount");
        if (kind == Kind.REUSABLE) return amountPerCraft;
        if (kind == Kind.CONSUMED)
            return SaturatingLongMath.multiply(crafts, amountPerCraft);
        return DurabilityInputMath.requiredTools(crafts, amountPerCraft, usesPerItem(key));
    }

    long requiredAmount(long crafts, IStackKey<?> key, long amountPerCraft,
                        MatchingStock<IStackKey<?>, net.minecraft.resources.ResourceLocation> stock)
    {
        if (kind != Kind.DURABILITY) return requiredAmount(crafts, key, amountPerCraft);
        long requiredUses = SaturatingLongMath.multiply(crafts, amountPerCraft);
        return stock.itemsForCapacity(key.getTypeId(), candidate -> matchesDurabilityVariant(key, candidate), requiredUses,
                this::usesPerItem, usesPerItem(key));
    }

    public long requiredAmount(long crafts, IStackKey<?> key, long amountPerCraft,
                               java.util.Map<IStackKey<?>, Long> stock)
    {
        if (kind != Kind.DURABILITY) return requiredAmount(crafts, key, amountPerCraft);
        long remaining = SaturatingLongMath.multiply(crafts, amountPerCraft);
        long items = 0;
        for (var entry : stock.entrySet())
        {
            if (entry.getValue() <= 0 || !matchesDurabilityVariant(key, entry.getKey())) continue;
            long capacity = usesPerItem(entry.getKey());
            long needed = remaining / capacity + (remaining % capacity == 0 ? 0 : 1);
            long used = Math.min(entry.getValue(), needed);
            items = SaturatingLongMath.add(items, used);
            long covered = SaturatingLongMath.multiply(used, capacity);
            if (covered >= remaining) return items;
            remaining -= covered;
        }
        return SaturatingLongMath.add(items, DurabilityInputMath.requiredTools(
                remaining, 1, usesPerItem(key)));
    }

    private long usesPerItem(IStackKey<?> key)
    {
        if (key instanceof ItemStackKey item)
        {
            var stack = item.getReadOnlyStack();
            if (stack.isDamageableItem()) return Math.max(1L,
                    ((long) stack.getMaxDamage() - stack.getDamageValue()) / damagePerCraft);
            return Long.MAX_VALUE;
        }
        return 1;
    }

    public static VirtualInputUse forRecipeSlot(net.minecraft.world.item.crafting.Recipe<?> recipe,
                                                 int slot, boolean reusableFallback)
    { return forRecipeSlot(recipe, slot, reusableFallback ? REUSABLE : CONSUMED); }

    public static VirtualInputUse forRecipeSlot(net.minecraft.world.item.crafting.Recipe<?> recipe,
                                                 int slot, VirtualInputUse fallback)
    {
        var descriptor = VirtualProvisionerRecipeRegistry.descriptor(recipe);
        return descriptor != null && slot >= 0 && slot < descriptor.inputs().size()
                ? descriptor.inputs().get(slot).use()
                : fallback == null ? CONSUMED : fallback;
    }
}
