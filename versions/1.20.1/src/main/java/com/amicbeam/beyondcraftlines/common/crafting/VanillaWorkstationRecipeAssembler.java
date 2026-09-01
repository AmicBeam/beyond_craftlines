package com.amicbeam.beyondcraftlines.common.crafting;

import com.amicbeam.beyondcraftlines.compat.crafting.RecipeHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/** Forge 1.20.1 construction of vanilla smithing and stonecutting recipe inputs. */
public final class VanillaWorkstationRecipeAssembler
{
    private VanillaWorkstationRecipeAssembler() {}

    public static ItemStack assemble(RecipeHolder<?> holder, String family,
                                     List<ItemStack> ingredients, ServerLevel level)
    {
        int expected = "minecraft:stonecutting".equals(family) ? 1
                : "minecraft:smithing".equals(family) ? 3 : -1;
        if (ingredients.size() != expected) return ItemStack.EMPTY;
        return assemble(holder.value(), new SimpleContainer(ingredients.toArray(ItemStack[]::new)), level);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack assemble(Recipe<?> recipe, SimpleContainer input, ServerLevel level)
    {
        Recipe raw = recipe;
        if (!raw.matches(input, level)) return ItemStack.EMPTY;
        return raw.assemble(input, level.registryAccess());
    }
}
