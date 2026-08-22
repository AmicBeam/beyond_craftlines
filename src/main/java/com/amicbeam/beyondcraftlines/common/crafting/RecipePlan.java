package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record RecipePlan(IStackKey<?> targetKey, long requested, List<Step> steps,
                         List<Material> missing, List<ReservedMaterial> reserved)
{
    public RecipePlan(ResourceLocation target, long requested, List<Step> steps, List<Material> missing)
    { this(itemKey(target), requested, steps, missing, List.of()); }

    public RecipePlan(ResourceLocation target, long requested, List<Step> steps,
                      List<Material> missing, List<ReservedMaterial> reserved)
    { this(itemKey(target), requested, steps, missing, reserved); }

    public RecipePlan
    {
        if (targetKey == null || targetKey.isEmpty() || requested < 1)
            throw new IllegalArgumentException("invalid recipe plan");
        steps = List.copyOf(steps);
        missing = List.copyOf(missing);
        reserved = List.copyOf(reserved);
    }

    public boolean craftable() { return missing.isEmpty(); }

    /** Legacy source id used by the item-oriented order status UI and persistence. */
    public ResourceLocation target()
    {
        if (targetKey instanceof ItemStackKey item)
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getSource());
        Object source = targetKey.getSource();
        try
        {
            Object id = source.getClass().getMethod("getRegistryName").invoke(source);
            if (id instanceof ResourceLocation location) return location;
        }
        catch (ReflectiveOperationException | RuntimeException ignored) {}
        return targetKey.getTypeId();
    }

    private static ItemStackKey itemKey(ResourceLocation target)
    { return new ItemStackKey(new net.minecraft.world.item.ItemStack(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(target))); }

    public record Material(IStackKey<?> key, long amount, int ingredientSlot, String inputGroup)
    {
        public Material(IStackKey<?> key, long amount) { this(key, amount, -1, "ingredients"); }
        public Material(IStackKey<?> key, long amount, int ingredientSlot)
        { this(key, amount, ingredientSlot, "ingredients"); }
        public Material
        {
            if (key == null || key.isEmpty() || amount < 1 || ingredientSlot < -1
                    || inputGroup == null || inputGroup.isBlank())
                throw new IllegalArgumentException("invalid material");
        }
        public ResourceLocation item()
        {
            return key instanceof ItemStackKey item
                    ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getSource()) : null;
        }
    }

    public record ReservedMaterial(IStackKey<?> key, long amount)
    {
        public ReservedMaterial
        {
            if (key == null || amount < 1) throw new IllegalArgumentException("invalid reserved material");
        }
        public ResourceLocation item()
        { return key instanceof ItemStackKey item
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getSource()) : null; }
    }

    public record Step(ResourceLocation recipe, String family, IStackKey<?> outputKey,
                       long outputPerCraft, long crafts, List<Material> inputs,
                       List<IngredientSelection> ingredientSelections, List<Integer> dependencies)
    {
        public Step(ResourceLocation recipe, String family, IStackKey<?> outputKey,
                    long outputPerCraft, long crafts, List<Material> inputs,
                    List<IngredientSelection> ingredientSelections)
        { this(recipe, family, outputKey, outputPerCraft, crafts, inputs, ingredientSelections, List.of()); }

        public Step(ResourceLocation recipe, String family, ResourceLocation output,
                    long outputPerCraft, long crafts, List<Material> inputs,
                    List<IngredientSelection> ingredientSelections)
        { this(recipe, family, new ItemStackKey(new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(output))), outputPerCraft, crafts,
                inputs, ingredientSelections, List.of()); }

        public Step
        {
            if (recipe == null || family == null || family.isBlank() || outputKey == null || outputKey.isEmpty()
                    || outputPerCraft < 1 || crafts < 1)
                throw new IllegalArgumentException("invalid recipe step");
            inputs = List.copyOf(inputs);
            ingredientSelections = List.copyOf(ingredientSelections);
            dependencies = List.copyOf(dependencies);
            if (dependencies.stream().anyMatch(index -> index == null || index < 0))
                throw new IllegalArgumentException("invalid recipe dependency");
        }

        /** Legacy item/source id used by item-only UI choices. */
        public ResourceLocation output()
        {
            if (outputKey instanceof ItemStackKey item)
                return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getSource());
            Object source = outputKey.getSource();
            try
            {
                var method = source.getClass().getMethod("getRegistryName");
                Object id = method.invoke(source);
                if (id instanceof ResourceLocation location) return location;
            }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
            String key = RecipeResourceResolver.sortKey(outputKey);
            ResourceLocation parsed = ResourceLocation.tryParse(key.substring(key.lastIndexOf('|') + 1));
            return parsed == null ? outputKey.getTypeId() : parsed;
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
