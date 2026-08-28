package com.amicbeam.beyondcraftlines.common.dashboard;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeResolutionOverrides;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Compact persistent representation of a fully selected dashboard recipe tree. */
public record DashboardRecipeConfig(RecipeResolutionOverrides overrides, boolean blockingMode)
{
    public static final DashboardRecipeConfig EMPTY = new DashboardRecipeConfig(
            RecipeResolutionOverrides.EMPTY, false);

    public DashboardRecipeConfig
    { overrides = overrides == null ? RecipeResolutionOverrides.EMPTY : overrides; }

    public boolean configured() { return !overrides.recipeChoices().isEmpty(); }
    public int choiceCount()
    { return overrides.recipeChoices().size() + overrides.ingredientChoices().size(); }

    /** Deterministic upper estimate used before materializing the block-entity NBT. */
    public int estimatedBytes()
    {
        long bytes = 64;
        for (var choice : overrides.recipeChoices())
            bytes += utf8(choice.output()) + utf8(choice.recipe().toString()) + 32L;
        for (var choice : overrides.ingredientChoices())
            bytes += utf8(choice.recipe().toString()) + utf8(choice.item().toString()) + 40L;
        return (int) Math.min(Integer.MAX_VALUE, bytes);
    }

    public CompoundTag save()
    {
        CompoundTag root = new CompoundTag();
        root.putBoolean("blocking", blockingMode);
        ListTag recipes = new ListTag();
        for (var choice : overrides.recipeChoices())
        {
            CompoundTag value = new CompoundTag();
            value.putString("output", choice.output());
            value.putString("recipe", choice.recipe().toString());
            recipes.add(value);
        }
        root.put("recipes", recipes);
        ListTag ingredients = new ListTag();
        for (var choice : overrides.ingredientChoices())
        {
            CompoundTag value = new CompoundTag();
            value.putString("recipe", choice.recipe().toString());
            value.putInt("slot", choice.slot());
            value.putString("item", choice.item().toString());
            ingredients.add(value);
        }
        root.put("ingredients", ingredients);
        return root;
    }

    public static DashboardRecipeConfig load(CompoundTag root)
    {
        if (root == null) return EMPTY;
        List<RecipeResolutionOverrides.RecipeChoice> recipes = new ArrayList<>();
        ListTag encodedRecipes = root.getList("recipes", Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedRecipes.size(); index++)
        {
            CompoundTag value = encodedRecipes.getCompound(index);
            ResourceLocation recipe = ResourceLocation.tryParse(value.getString("recipe"));
            String output = value.getString("output");
            if (recipe != null && !output.isBlank())
                recipes.add(new RecipeResolutionOverrides.RecipeChoice(output, recipe));
        }
        List<RecipeResolutionOverrides.IngredientChoice> ingredients = new ArrayList<>();
        ListTag encodedIngredients = root.getList("ingredients", Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedIngredients.size(); index++)
        {
            CompoundTag value = encodedIngredients.getCompound(index);
            ResourceLocation recipe = ResourceLocation.tryParse(value.getString("recipe"));
            ResourceLocation item = ResourceLocation.tryParse(value.getString("item"));
            int slot = value.getInt("slot");
            if (recipe != null && item != null && slot >= 0)
                ingredients.add(new RecipeResolutionOverrides.IngredientChoice(recipe, slot, item));
        }
        try { return new DashboardRecipeConfig(
                new RecipeResolutionOverrides(recipes, ingredients), root.getBoolean("blocking")); }
        catch (RuntimeException ignored) { return EMPTY; }
    }

    private static int utf8(String value)
    { return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length; }
}
