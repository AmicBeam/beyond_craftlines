package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;

/** Unified recipe enumeration: authoritative manager on servers, JEI-fed catalog on clients. */
public final class RecipeCatalog
{
    private static volatile List<RecipeHolder<?>> clientRecipes = List.of();

    private RecipeCatalog() {}

    public static List<RecipeHolder<?>> forLevel(Level level)
    {
        if (level instanceof ServerLevel serverLevel)
            return List.copyOf(serverLevel.recipeAccess().getRecipes());
        return clientRecipes;
    }

    public static void setClientRecipes(Collection<RecipeHolder<?>> recipes)
    {
        clientRecipes = List.copyOf(recipes);
        RecipePlanningService.clearRecipeCache();
    }

    public static void clearClient()
    {
        clientRecipes = List.of();
        RecipePlanningService.clearRecipeCache();
    }
}
