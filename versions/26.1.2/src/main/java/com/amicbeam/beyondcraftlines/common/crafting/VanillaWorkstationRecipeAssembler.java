package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

import java.util.List;

/** Minecraft 26.1 construction of vanilla smithing and stonecutting recipe inputs. */
public final class VanillaWorkstationRecipeAssembler
{
    private VanillaWorkstationRecipeAssembler() {}

    public static ItemStack assemble(RecipeHolder<?> holder, String family,
                                     List<ItemStack> ingredients, ServerLevel level)
    {
        RecipeInput input = switch (family)
        {
            case "minecraft:stonecutting" -> ingredients.size() == 1
                    ? new SingleRecipeInput(ingredients.getFirst()) : null;
            case "minecraft:smithing" -> ingredients.size() == 3
                    ? new SmithingRecipeInput(ingredients.get(0), ingredients.get(1), ingredients.get(2)) : null;
            default -> null;
        };
        return input == null ? ItemStack.EMPTY : assemble(holder.value(), input, level);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack assemble(Recipe<?> recipe, RecipeInput input, ServerLevel level)
    {
        Recipe raw = recipe;
        if (!raw.matches(input, level)) return ItemStack.EMPTY;
        return raw.assemble(input);
    }
}
