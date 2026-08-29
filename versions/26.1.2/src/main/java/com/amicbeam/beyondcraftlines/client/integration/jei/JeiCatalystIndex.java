package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalog;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.RecipeIngredientRole;
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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static volatile Map<Identifier, Set<Identifier>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<Identifier, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<Identifier, Set<String>> INPUT_GROUPS_BY_TYPE = Map.of();
    private static volatile Map<Identifier, RecipeHolder<?>> RECIPES_BY_ID = Map.of();
    private static volatile IJeiRuntime runtime;

    private JeiCatalystIndex() {}

    public static void rebuild(IJeiRuntime runtime)
    {
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        Map<Identifier, LinkedHashSet<Identifier>> building = new HashMap<>();
        Map<Identifier, Component> titles = new HashMap<>();
        Map<Identifier, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        Map<Identifier, Set<String>> inputGroups = new HashMap<>();
        int[] remainingLayouts = {16_384};
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            try
            {
                var recipeType = category.getRecipeType();
                Identifier typeId = recipeType.getUid();
                titles.put(typeId, category.getTitle());
                manager.createCraftingStationLookup(recipeType).includeHidden().getItemStack()
                        .filter(stack -> !stack.isEmpty())
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .forEach(item -> building.computeIfAbsent(item, ignored ->
                                new LinkedHashSet<>()).add(typeId));
                inputGroups.put(typeId, collectRecipes(runtime, category, recipes, remainingLayouts));
            }
            catch (RuntimeException | LinkageError exception)
            {
                LOGGER.warn("Unable to index JEI recipe category {}",
                        category.getClass().getName(), exception);
            }
        });
        Map<Identifier, Set<Identifier>> frozen = new HashMap<>();
        building.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
        TYPES_BY_CATALYST = Map.copyOf(frozen);
        TITLES_BY_TYPE = Map.copyOf(titles);
        INPUT_GROUPS_BY_TYPE = Map.copyOf(inputGroups);
        RECIPES_BY_ID = Map.copyOf(recipes);
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

    public static Optional<Component> recipeTypeTitle(Identifier type)
    {
        return Optional.ofNullable(TITLES_BY_TYPE.get(type));
    }

    /** All recipe categories currently exposed by JEI, for the provisioner's manual fallback. */
    public static Set<Identifier> recipeTypes()
    {
        return TITLES_BY_TYPE.keySet();
    }

    public static Map<Identifier, Set<String>> inputGroupsFor(Set<Identifier> types)
    {
        Map<Identifier, Set<String>> result = new HashMap<>();
        types.forEach(type -> result.put(type, INPUT_GROUPS_BY_TYPE.getOrDefault(type, Set.of())));
        return Map.copyOf(result);
    }

    /** Uses the synced client recipe access to mirror the server's representative-hint validation. */
    public static Set<Identifier> recipeTypes(Set<String> loadedFamilies,
                                                    Map<String, Set<String>> aliases,
                                                    boolean debugMappings)
    {
        if (TITLES_BY_TYPE.isEmpty()) refresh();
        return recipeTypes();
    }

    public static void clear()
    {
        runtime = null;
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        INPUT_GROUPS_BY_TYPE = Map.of();
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        RECIPES_BY_ID = Map.of();
        RecipeCatalog.clearClient();
    }

    private static <T> Set<String> collectRecipes(IJeiRuntime runtime,
                                           IRecipeCategory<T> category,
                                           Map<Identifier, RecipeHolder<?>> recipes,
                                           int[] remainingLayouts)
    {
        IRecipeManager manager = runtime.getRecipeManager();
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        var focuses = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        if (remainingLayouts[0] <= 0) return Set.of();
        manager.createRecipeLookup(category.getRecipeType()).includeHidden().get()
                .limit(remainingLayouts[0]).forEach(value -> {
            RecipeHolder<?> holder;
            if (value instanceof RecipeHolder<?> recipeHolder)
            {
                recipes.putIfAbsent(recipeHolder.id().identifier(), recipeHolder);
            }
            else if (value instanceof Recipe<?> recipe)
            {
                Identifier id = category.getIdentifier(value);
                if (id != null)
                {
                    holder = holder(id, recipe);
                    recipes.putIfAbsent(id, holder);
                }
            }
            manager.createRecipeLayoutDrawable(category, value, focuses).ifPresent(layout -> {
                var captured = JeiVirtualRecipeLayouts.capture(category.getRecipeType().getUid(), layout);
                if (captured == null) return;
                JeiVirtualRecipeLayouts.register(captured);
                remainingLayouts[0]--;
                captured.inputs().forEach(input -> groups.add(input.inputGroup()));
            });
        });
        return Set.copyOf(groups);
    }

    private static <R extends Recipe<?>> RecipeHolder<R> holder(Identifier id, R recipe)
    {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe);
    }
}
