package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Fixed planning slots, real vanilla matching, assembly and remaining items. */
public final class FireworkRocketCraftingVariant extends ShapelessRecipe
{
    private final FireworkRocketRecipe source;
    private final ItemStack preview;
    public ItemStack preview() { return preview.copy(); }
    public FireworkRocketCraftingVariant(FireworkRocketRecipe source, ItemStack output, int flight)
    {
        super(new Recipe.CommonInfo(false), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                ItemStackTemplate.fromNonEmptyStack(output), ingredients(flight));
        this.preview = output.copy();
        this.source = source;
    }
    public boolean acceptsOutput(ItemStack actual)
    { return !actual.isEmpty() && actual.getCount() == preview.getCount()
            && ItemStack.isSameItemSameComponents(actual, preview); }

    private static NonNullList<Ingredient> ingredients(int flight)
    {
        if (flight < 1 || flight > 3) throw new IllegalArgumentException("invalid rocket duration");
        NonNullList<Ingredient> inputs = NonNullList.create();
        inputs.add(Ingredient.of(Items.PAPER));
        for (int i = 0; i < flight; i++) inputs.add(Ingredient.of(Items.GUNPOWDER));
        return inputs;
    }
    @Override public boolean matches(CraftingInput input, Level level)
    { return super.matches(input, level) && source.matches(input, level); }
    @Override public ItemStack assemble(CraftingInput input)
    { return source.assemble(input); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input)
    { return source.getRemainingItems(input); }
}
