package net.minecraft.world.item.crafting;

import net.minecraft.resources.ResourceLocation;

/** 1.21-shaped recipe identity used internally by the shared planner. */
public record RecipeHolder<T extends Recipe<?>>(ResourceLocation id, T value) {}
