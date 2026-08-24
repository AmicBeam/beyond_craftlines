package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalog;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeFamilyHint;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
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
import java.util.List;
import java.util.Set;

/** Client-only snapshot of the machine relationships JEI actually displays. */
public final class JeiCatalystIndex
{
    private static volatile Map<Identifier, Set<Identifier>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<Identifier, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<Identifier, List<RecipeFamilyHint>> HINTS_BY_TYPE = Map.of();
    private static volatile IJeiRuntime runtime;

    private JeiCatalystIndex() {}

    public static void rebuild(IJeiRuntime runtime)
    {
        JeiCatalystIndex.runtime = runtime;
        Map<Identifier, LinkedHashSet<Identifier>> building = new HashMap<>();
        Map<Identifier, Component> titles = new HashMap<>();
        Map<Identifier, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        Map<Identifier, List<RecipeFamilyHint>> hints = new HashMap<>();
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            var recipeType = category.getRecipeType();
            Identifier typeId = recipeType.getUid();
            titles.put(typeId, category.getTitle());
            manager.createCraftingStationLookup(recipeType).includeHidden().getItemStack()
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    .forEach(item -> building.computeIfAbsent(item, ignored -> new LinkedHashSet<>()).add(typeId));
            hints.put(typeId, collectRecipes(manager, category, typeId, recipes));
        });
        Map<Identifier, Set<Identifier>> frozen = new HashMap<>();
        building.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
        TYPES_BY_CATALYST = Map.copyOf(frozen);
        TITLES_BY_TYPE = Map.copyOf(titles);
        HINTS_BY_TYPE = Map.copyOf(hints);
        RecipeCatalog.setClientRecipes(recipes.values());
    }

    public static Set<Identifier> recipeTypesFor(ItemStack catalyst)
    {
        if (catalyst.isEmpty()) return Set.of();
        return TYPES_BY_CATALYST.getOrDefault(BuiltInRegistries.ITEM.getKey(catalyst.getItem()), Set.of());
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    public static List<RecipeFamilyHint> hintsFor(Set<Identifier> types)
    {
        return types.stream().sorted(java.util.Comparator.comparing(Identifier::toString))
                .flatMap(type -> HINTS_BY_TYPE.getOrDefault(type, List.of()).stream())
                .limit(128).toList();
    }

    public static Optional<Component> recipeTypeTitle(Identifier type)
    {
        return Optional.ofNullable(TITLES_BY_TYPE.get(type));
    }

    /** All recipe categories currently exposed by JEI, for the provisioner's manual fallback. */
    public static Set<Identifier> recipeTypes()
    {
        return TITLES_BY_TYPE.keySet();
    }

    public static void clear()
    {
        runtime = null;
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        HINTS_BY_TYPE = Map.of();
        RecipeCatalog.clearClient();
    }

    private static <T> List<RecipeFamilyHint> collectRecipes(IRecipeManager manager,
                                           IRecipeCategory<T> category, Identifier typeId,
                                           Map<Identifier, RecipeHolder<?>> recipes)
    {
        Map<String, RecipeFamilyHint> byFamily = new LinkedHashMap<>();
        manager.createRecipeLookup(category.getRecipeType()).includeHidden().get().forEach(value -> {
            RecipeHolder<?> holder;
            if (value instanceof RecipeHolder<?> recipeHolder)
            {
                recipes.putIfAbsent(recipeHolder.id().identifier(), recipeHolder);
                addHint(byFamily, typeId, recipeHolder);
                return;
            }
            if (!(value instanceof Recipe<?> recipe)) return;
            Identifier id = category.getIdentifier(value);
            if (id == null) return;
            holder = holder(id, recipe);
            recipes.putIfAbsent(id, holder);
            addHint(byFamily, typeId, holder);
        });
        return List.copyOf(byFamily.values());
    }

    private static void addHint(Map<String, RecipeFamilyHint> hints, Identifier typeId,
                                RecipeHolder<?> holder)
    {
        try
        {
            String family = RecipePlanningService.family(holder);
            if (family == null || family.isBlank()) return;
            hints.putIfAbsent(family, new RecipeFamilyHint(
                    typeId.toString(), family, holder.id().identifier().toString()));
        }
        catch (RuntimeException ignored)
        {
            // A malformed third-party JEI recipe must not abort the entire catalyst index.
        }
    }

    private static <R extends Recipe<?>> RecipeHolder<R> holder(Identifier id, R recipe)
    {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe);
    }
}
