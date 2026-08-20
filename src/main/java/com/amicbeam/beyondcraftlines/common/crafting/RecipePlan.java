package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record RecipePlan(ResourceLocation target, long requested, List<Step> steps,
                         List<Material> missing)
{
    public RecipePlan
    {
        if (target == null || requested < 1) throw new IllegalArgumentException("invalid recipe plan");
        steps = List.copyOf(steps);
        missing = List.copyOf(missing);
    }

    public boolean craftable() { return missing.isEmpty(); }

    public record Material(ResourceLocation item, long amount)
    {
        public Material
        {
            if (item == null || amount < 1) throw new IllegalArgumentException("invalid material");
        }
    }

    public record Step(ResourceLocation recipe, String family, ResourceLocation output,
                       long outputPerCraft, long crafts, List<Material> inputs)
    {
        public Step
        {
            if (recipe == null || family == null || family.isBlank() || output == null
                    || outputPerCraft < 1 || crafts < 1)
                throw new IllegalArgumentException("invalid recipe step");
            inputs = List.copyOf(inputs);
        }
    }
}
