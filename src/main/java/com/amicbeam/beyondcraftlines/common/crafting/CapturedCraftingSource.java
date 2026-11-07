package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.level.Level;
import java.util.List;

/** Exact native source and occupied grid positions for one JEI crafting variant. */
public record CapturedCraftingSource(String recipeId, int width, int height, List<Integer> slots)
{
    public CapturedCraftingSource
    {
        if (recipeId == null || recipeId.length() > 256 || ResourceLocation.tryParse(recipeId) == null
                || width < 1 || width > 3 || height < 1 || height > 3 || slots == null
                || slots.isEmpty() || slots.size() > 9 || slots.stream().distinct().count() != slots.size()
                || slots.stream().anyMatch(slot -> slot == null || slot < 0 || slot >= width * height))
            throw new IllegalArgumentException("invalid captured crafting source");
        slots = List.copyOf(slots);
    }

    public static boolean requiresCapture(net.minecraft.world.item.crafting.Recipe<?> recipe)
    { return recipe instanceof CraftingRecipe && !(recipe instanceof FireworkRocketRecipe)
            && (recipe.isSpecial() || RecipeIngredientResolver.ingredients(recipe).isEmpty()); }

    public CraftingRecipe resolve(Level level)
    {
        var holder = level.getRecipeManager().byKey(ResourceLocation.parse(recipeId)).orElse(null);
        // Existing server-derived rocket variants retain their stricter identity contract.
        return holder != null && holder.value() instanceof CraftingRecipe recipe
                && !(recipe instanceof FireworkRocketRecipe) ? recipe : null;
    }

    public String encode()
    { return recipeId + "|" + width + "|" + height + "|" + slots.stream().map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(",")); }

    public static CapturedCraftingSource decode(String encoded)
    {
        if (encoded.isEmpty()) return null;
        String[] parts = encoded.split("\\|", -1);
        if (encoded.length() > 320 || parts.length != 4) throw new IllegalArgumentException("invalid crafting source encoding");
        return new CapturedCraftingSource(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                java.util.Arrays.stream(parts[3].split(",", -1)).map(Integer::parseInt).toList());
    }

    public static String encode(CapturedCraftingSource source) { return source == null ? "" : source.encode(); }
}
