package com.amicbeam.beyondcraftlines.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-player defaults for recipe-tree choices. Values are validated again before use. */
public final class PlannerPreferences
{
    private static final String ROOT = "BeyondCraftlinesPlannerPreferences";
    private static final String RECIPES = "Recipes";
    private static final String INGREDIENTS = "Ingredients";
    private static final int MAX_ENTRIES = 1024;

    private PlannerPreferences() {}

    public static Snapshot read(Player player)
    {
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        LinkedHashMap<ResourceLocation, ResourceLocation> recipes = new LinkedHashMap<>();
        ListTag recipeList = root.getList(RECIPES, Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(recipeList.size(), MAX_ENTRIES); index++)
        {
            CompoundTag entry = recipeList.getCompound(index);
            ResourceLocation output = ResourceLocation.tryParse(entry.getString("Output"));
            ResourceLocation recipe = ResourceLocation.tryParse(entry.getString("Recipe"));
            if (output != null && recipe != null) recipes.put(output, recipe);
        }
        LinkedHashMap<IngredientKey, ResourceLocation> ingredients = new LinkedHashMap<>();
        ListTag ingredientList = root.getList(INGREDIENTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(ingredientList.size(), MAX_ENTRIES); index++)
        {
            CompoundTag entry = ingredientList.getCompound(index);
            ResourceLocation recipe = ResourceLocation.tryParse(entry.getString("Recipe"));
            ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
            int slot = entry.getInt("Slot");
            if (recipe != null && item != null && slot >= 0)
                ingredients.put(new IngredientKey(recipe, slot), item);
        }
        return new Snapshot(Map.copyOf(recipes), Map.copyOf(ingredients));
    }

    public static void setRecipe(Player player, ResourceLocation output, ResourceLocation recipe)
    {
        Snapshot old = read(player);
        LinkedHashMap<ResourceLocation, ResourceLocation> recipes = new LinkedHashMap<>(old.recipes());
        if (recipe == null) recipes.remove(output); else recipes.put(output, recipe);
        write(player, recipes, old.ingredients());
    }

    public static void setIngredient(Player player, IngredientKey key, ResourceLocation item)
    {
        Snapshot old = read(player);
        LinkedHashMap<IngredientKey, ResourceLocation> ingredients = new LinkedHashMap<>(old.ingredients());
        if (item == null) ingredients.remove(key); else ingredients.put(key, item);
        write(player, old.recipes(), ingredients);
    }

    private static void write(Player player, Map<ResourceLocation, ResourceLocation> recipes,
                              Map<IngredientKey, ResourceLocation> ingredients)
    {
        CompoundTag root = new CompoundTag();
        ListTag recipeList = new ListTag();
        recipes.entrySet().stream().limit(MAX_ENTRIES).forEach(entry -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Output", entry.getKey().toString());
            tag.putString("Recipe", entry.getValue().toString());
            recipeList.add(tag);
        });
        root.put(RECIPES, recipeList);
        ListTag ingredientList = new ListTag();
        ingredients.entrySet().stream().limit(MAX_ENTRIES).forEach(entry -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Recipe", entry.getKey().recipe().toString());
            tag.putInt("Slot", entry.getKey().slot());
            tag.putString("Item", entry.getValue().toString());
            ingredientList.add(tag);
        });
        root.put(INGREDIENTS, ingredientList);
        player.getPersistentData().put(ROOT, root);
    }

    public record IngredientKey(ResourceLocation recipe, int slot) {}
    public record Snapshot(Map<ResourceLocation, ResourceLocation> recipes,
                           Map<IngredientKey, ResourceLocation> ingredients) {}
}
