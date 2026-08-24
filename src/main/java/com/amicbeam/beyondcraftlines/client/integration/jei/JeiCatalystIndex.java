package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeFamilyHint;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Client-only snapshot of the machine relationships JEI actually displays. */
public final class JeiCatalystIndex
{
    private static volatile Map<Item, Set<ResourceLocation>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<ResourceLocation, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<ResourceLocation, List<RecipeFamilyHint>> HINTS_BY_TYPE = Map.of();
    private static volatile IJeiRuntime runtime;

    private JeiCatalystIndex() {}

    public static void rebuild(IJeiRuntime runtime)
    {
        JeiCatalystIndex.runtime = runtime;
        Map<Item, LinkedHashSet<ResourceLocation>> building = new HashMap<>();
        Map<ResourceLocation, Component> titles = new HashMap<>();
        Map<ResourceLocation, List<RecipeFamilyHint>> hints = new HashMap<>();
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            var recipeType = category.getRecipeType();
            ResourceLocation typeId = recipeType.getUid();
            titles.put(typeId, category.getTitle());
            manager.createRecipeCatalystLookup(recipeType).includeHidden().getItemStack()
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::getItem)
                    .forEach(item -> building.computeIfAbsent(item, ignored -> new LinkedHashSet<>()).add(typeId));
            hints.put(typeId, collectHints(manager, category, typeId));
        });
        Map<Item, Set<ResourceLocation>> frozen = new HashMap<>();
        building.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
        TYPES_BY_CATALYST = Map.copyOf(frozen);
        TITLES_BY_TYPE = Map.copyOf(titles);
        HINTS_BY_TYPE = Map.copyOf(hints);
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    public static Set<ResourceLocation> recipeTypesFor(ItemStack catalyst)
    {
        if (catalyst.isEmpty()) return Set.of();
        Item item = catalyst.getItem();
        Set<ResourceLocation> types = TYPES_BY_CATALYST.get(item);
        if (types != null) return types;

        // A recipe reload can complete after JEI first publishes its runtime. Rebuild once at the
        // point of use so machine binding and provisioner scans do not remain stuck with that stale snapshot.
        refresh();
        return TYPES_BY_CATALYST.getOrDefault(item, Set.of());
    }

    public static Optional<Component> recipeTypeTitle(ResourceLocation type)
    {
        return Optional.ofNullable(TITLES_BY_TYPE.get(type));
    }

    /** All recipe categories currently exposed by JEI, for the provisioner's manual fallback. */
    public static Set<ResourceLocation> recipeTypes()
    {
        return TITLES_BY_TYPE.keySet();
    }

    public static List<RecipeFamilyHint> hintsFor(Set<ResourceLocation> types)
    {
        return types.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .flatMap(type -> HINTS_BY_TYPE.getOrDefault(type, List.of()).stream())
                .limit(128).toList();
    }

    public static void clear()
    {
        runtime = null;
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        HINTS_BY_TYPE = Map.of();
    }

    private static <T> List<RecipeFamilyHint> collectHints(IRecipeManager manager,
                                                            IRecipeCategory<T> category,
                                                            ResourceLocation typeId)
    {
        Map<String, RecipeFamilyHint> byFamily = new java.util.LinkedHashMap<>();
        manager.createRecipeLookup(category.getRecipeType()).includeHidden().get().limit(64).forEach(value -> {
            RecipeHolder<?> holder = null;
            if (value instanceof RecipeHolder<?> recipeHolder) holder = recipeHolder;
            else if (value instanceof Recipe<?> recipe)
            {
                ResourceLocation id = category.getRegistryName(value);
                if (id != null) holder = holder(id, recipe);
            }
            if (holder == null) return;
            try
            {
                String family = RecipePlanningService.family(holder);
                if (family == null || family.isBlank()) return;
                byFamily.putIfAbsent(family, new RecipeFamilyHint(
                        typeId.toString(), family, holder.id().toString()));
            }
            catch (RuntimeException ignored)
            {
                // A malformed third-party JEI recipe must not abort the entire catalyst index.
            }
        });
        return List.copyOf(byFamily.values());
    }

    private static <R extends Recipe<?>> RecipeHolder<R> holder(ResourceLocation id, R recipe)
    { return new RecipeHolder<>(id, recipe); }
}
