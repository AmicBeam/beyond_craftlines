package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Builds a concrete resource output from datapack-declared type, ID, and numeric amount member. */
final class MappedRecipeOutput
{
    private MappedRecipeOutput() {}

    static KeyAmount resolve(Object recipe, RecipeIoProfileRegistry.OutputMapping mapping)
    {
        Object rawAmount = RecipeReflection.readPublicMember(recipe, mapping.amountField());
        if (!(rawAmount instanceof Number number) || number.longValue() <= 0) return null;
        try
        {
            Object stack = switch (mapping.type())
            {
                case ITEM -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(mapping.id())));
                case FLUID -> new FluidStack(
                        BuiltInRegistries.FLUID.getValue(Identifier.parse(mapping.id())), 1);
            };
            KeyAmount converted = RecipeResourceResolver.fromStack(stack);
            return converted == null || converted.isEmpty() ? null
                    : new KeyAmount(converted.key(), number.longValue());
        }
        catch (RuntimeException | LinkageError ignored) { return null; }
    }
}
