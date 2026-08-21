package com.amicbeam.beyondcraftlines.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
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
        CompoundTag root = player.getPersistentData().getCompoundOrEmpty(ROOT);
        LinkedHashMap<Identifier, Identifier> recipes = new LinkedHashMap<>();
        ListTag recipeList = root.getListOrEmpty(RECIPES);
        for (int index = 0; index < Math.min(recipeList.size(), MAX_ENTRIES); index++)
        {
            CompoundTag entry = recipeList.getCompoundOrEmpty(index);
            Identifier output = Identifier.tryParse(entry.getStringOr("Output", ""));
            Identifier recipe = Identifier.tryParse(entry.getStringOr("Recipe", ""));
            if (output != null && recipe != null) recipes.put(output, recipe);
        }
        LinkedHashMap<IngredientKey, Identifier> ingredients = new LinkedHashMap<>();
        ListTag ingredientList = root.getListOrEmpty(INGREDIENTS);
        for (int index = 0; index < Math.min(ingredientList.size(), MAX_ENTRIES); index++)
        {
            CompoundTag entry = ingredientList.getCompoundOrEmpty(index);
            Identifier recipe = Identifier.tryParse(entry.getStringOr("Recipe", ""));
            Identifier item = Identifier.tryParse(entry.getStringOr("Item", ""));
            int slot = entry.getIntOr("Slot", -1);
            if (recipe != null && item != null && slot >= 0)
                ingredients.put(new IngredientKey(recipe, slot), item);
        }
        return new Snapshot(Map.copyOf(recipes), Map.copyOf(ingredients));
    }

    public static void setRecipe(Player player, Identifier output, Identifier recipe)
    {
        Snapshot old = read(player);
        LinkedHashMap<Identifier, Identifier> recipes = new LinkedHashMap<>(old.recipes());
        if (recipe == null) recipes.remove(output); else recipes.put(output, recipe);
        write(player, recipes, old.ingredients());
    }

    public static void setIngredient(Player player, IngredientKey key, Identifier item)
    {
        Snapshot old = read(player);
        LinkedHashMap<IngredientKey, Identifier> ingredients = new LinkedHashMap<>(old.ingredients());
        if (item == null) ingredients.remove(key); else ingredients.put(key, item);
        write(player, old.recipes(), ingredients);
    }

    private static void write(Player player, Map<Identifier, Identifier> recipes,
                              Map<IngredientKey, Identifier> ingredients)
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

    public record IngredientKey(Identifier recipe, int slot) {}
    public record Snapshot(Map<Identifier, Identifier> recipes,
                           Map<IngredientKey, Identifier> ingredients) {}
}
