package com.amicbeam.beyondcraftlines.compat.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/** 1.21-shaped crafting input backed by the 1.20.1 CraftingContainer contract. */
public final class CraftingInput extends SimpleContainer implements CraftingContainer {
    private final int width;
    private final int height;

    private CraftingInput(int width, int height, List<ItemStack> values) {
        super(width * height);
        this.width = width;
        this.height = height;
        for (int i = 0; i < Math.min(values.size(), getContainerSize()); i++) setItem(i, values.get(i));
    }

    public static CraftingInput of(int width, int height, List<ItemStack> values) {
        return new CraftingInput(width, height, values);
    }

    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    public int size() { return getContainerSize(); }
    @Override public List<ItemStack> getItems() {
        List<ItemStack> result = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) result.add(getItem(i));
        return result;
    }
}
