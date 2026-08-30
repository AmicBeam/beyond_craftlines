package com.amicbeam.beyondcraftlines.common.crafting;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Reads item inputs from both vanilla recipes and common third-party recipe APIs.
 * Some machine mods intentionally expose an empty vanilla ingredient list and keep
 * their real item inputs behind objects such as getInput().getRepresentations().
 */
public final class RecipeIngredientResolver
{
    private static final Map<Recipe<?>, List<Ingredient>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RecipeIngredientResolver() {}

    public static List<Ingredient> ingredients(Recipe<?> recipe)
    {
        List<Ingredient> vanilla = safeGet(recipe::getIngredients);
        if (!vanilla.isEmpty()) return vanilla;
        return CACHE.computeIfAbsent(recipe, RecipeIngredientResolver::customItemInputs);
    }

    static <T> List<T> safeGet(Supplier<? extends Collection<T>> values)
    {
        try
        { return safeCopy(values.get()); }
        catch (LinkageError | RuntimeException ignored)
        { return List.of(); }
    }

    static <T> List<T> safeCopy(Collection<T> values)
    {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(Objects::nonNull).toList();
    }

    public static void clearCache()
    {
        CACHE.clear();
        RecipeResourceResolver.clearCache();
    }

    private static List<Ingredient> customItemInputs(Recipe<?> recipe)
    {
        return RecipeResourceResolver.ingredients(recipe).stream()
                .filter(RecipeResourceResolver.ResourceIngredient::isItem)
                .map(RecipeResourceResolver.ResourceIngredient::itemIngredient)
                .toList();
    }
}
