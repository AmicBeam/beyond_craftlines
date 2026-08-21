package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalog;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Client-only snapshot of the machine relationships JEI actually displays. */
public final class JeiCatalystIndex
{
    private static volatile Map<Identifier, Set<Identifier>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<Identifier, Component> TITLES_BY_TYPE = Map.of();

    private JeiCatalystIndex() {}

    public static void rebuild(IJeiRuntime runtime)
    {
        Map<Identifier, LinkedHashSet<Identifier>> building = new HashMap<>();
        Map<Identifier, Component> titles = new HashMap<>();
        Map<Identifier, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            var recipeType = category.getRecipeType();
            Identifier typeId = recipeType.getUid();
            titles.put(typeId, category.getTitle());
            manager.createCraftingStationLookup(recipeType).includeHidden().getItemStack()
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    .forEach(item -> building.computeIfAbsent(item, ignored -> new LinkedHashSet<>()).add(typeId));
            collectRecipes(manager, category, recipes);
        });
        Map<Identifier, Set<Identifier>> frozen = new HashMap<>();
        building.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
        TYPES_BY_CATALYST = Map.copyOf(frozen);
        TITLES_BY_TYPE = Map.copyOf(titles);
        RecipeCatalog.setClientRecipes(recipes.values());
    }

    public static Set<Identifier> recipeTypesFor(ItemStack catalyst)
    {
        if (catalyst.isEmpty()) return Set.of();
        return TYPES_BY_CATALYST.getOrDefault(BuiltInRegistries.ITEM.getKey(catalyst.getItem()), Set.of());
    }

    public static Optional<Component> recipeTypeTitle(Identifier type)
    {
        return Optional.ofNullable(TITLES_BY_TYPE.get(type));
    }

    public static void clear()
    {
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        RecipeCatalog.clearClient();
    }

    private static <T> void collectRecipes(IRecipeManager manager, IRecipeCategory<T> category,
                                           Map<Identifier, RecipeHolder<?>> recipes)
    {
        manager.createRecipeLookup(category.getRecipeType()).includeHidden().get().forEach(value -> {
            if (value instanceof RecipeHolder<?> holder)
            {
                recipes.putIfAbsent(holder.id().identifier(), holder);
                return;
            }
            if (!(value instanceof Recipe<?> recipe)) return;
            Identifier id = category.getIdentifier(value);
            if (id == null) return;
            recipes.putIfAbsent(id, holder(id, recipe));
        });
    }

    private static <R extends Recipe<?>> RecipeHolder<R> holder(Identifier id, R recipe)
    {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe);
    }
}
