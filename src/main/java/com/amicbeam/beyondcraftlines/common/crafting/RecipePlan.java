package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record RecipePlan(ResourceLocation target, long requested, List<Step> steps,
                         List<Material> missing, List<ReservedMaterial> reserved)
{
    public RecipePlan(ResourceLocation target, long requested, List<Step> steps, List<Material> missing)
    { this(target, requested, steps, missing, List.of()); }

    public RecipePlan
    {
        if (target == null || requested < 1) throw new IllegalArgumentException("invalid recipe plan");
        steps = List.copyOf(steps);
        missing = List.copyOf(missing);
        reserved = List.copyOf(reserved);
    }

    public boolean craftable() { return missing.isEmpty(); }

    public record Material(ResourceLocation item, long amount, int ingredientSlot)
    {
        public Material(ResourceLocation item, long amount) { this(item, amount, -1); }
        public Material
        {
            if (item == null || amount < 1 || ingredientSlot < -1)
                throw new IllegalArgumentException("invalid material");
        }
    }

    public record ReservedMaterial(ItemStackKey key, long amount)
    {
        public ReservedMaterial
        {
            if (key == null || amount < 1) throw new IllegalArgumentException("invalid reserved material");
        }
        public ResourceLocation item()
        { return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key.getSource()); }
    }

    public record Step(ResourceLocation recipe, String family, ResourceLocation output,
                       long outputPerCraft, long crafts, List<Material> inputs,
                       List<IngredientSelection> ingredientSelections)
    {
        public Step
        {
            if (recipe == null || family == null || family.isBlank() || output == null
                    || outputPerCraft < 1 || crafts < 1)
                throw new IllegalArgumentException("invalid recipe step");
            inputs = List.copyOf(inputs);
            ingredientSelections = List.copyOf(ingredientSelections);
        }
    }

    /** Concrete item selected for one recipe ingredient slot. Empty recipe slots are omitted. */
    public record IngredientSelection(int slot, ResourceLocation item)
    {
        public IngredientSelection
        {
            if (slot < 0 || item == null) throw new IllegalArgumentException("invalid ingredient selection");
        }
    }
}
