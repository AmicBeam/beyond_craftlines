package com.amicbeam.beyondcraftlines.compat;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import com.amicbeam.beyondcraftlines.compat.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

public final class RecipeManagerCompat {
    private RecipeManagerCompat() {}

    public static List<RecipeHolder<?>> ordered(RecipeManager manager) {
        return manager.getRecipes().stream().map(RecipeManagerCompat::holder)
                .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList();
    }

    public static java.util.Optional<RecipeHolder<?>> byKey(RecipeManager manager, ResourceLocation id) {
        return manager.byKey(id).map(recipe -> new RecipeHolder<>(id, recipe));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecipeHolder<?> holder(Recipe<?> recipe) {
        return new RecipeHolder(recipe.getId(), recipe);
    }
}
