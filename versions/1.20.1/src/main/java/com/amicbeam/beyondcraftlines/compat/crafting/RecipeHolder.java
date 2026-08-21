package com.amicbeam.beyondcraftlines.compat.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/** 1.21-shaped recipe identity used internally by the shared planner. */
public record RecipeHolder<T extends Recipe<?>>(ResourceLocation id, T value) {}
