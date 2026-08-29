package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalog;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lightweight JEI metadata plus a target-driven, frame-budgeted virtual recipe index. */
public final class JeiCatalystIndex
{
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final int MAX_LAYOUTS_PER_FRAME = 32;
    private static final long MAX_INDEX_NANOS_PER_FRAME = 2_000_000L;
    private static volatile Map<Identifier, Set<Identifier>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<Identifier, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<Identifier, Set<String>> INPUT_GROUPS_BY_TYPE = Map.of();
    private static volatile Map<Identifier, IRecipeCategory<?>> CATEGORIES_BY_TYPE = Map.of();
    private static final Map<Identifier, RecipeHolder<?>> RECIPES_BY_ID = new LinkedHashMap<>();
    private static final ArrayDeque<SearchTask> SEARCH_QUEUE = new ArrayDeque<>();
    private static final ArrayDeque<SearchTask> GROUP_QUEUE = new ArrayDeque<>();
    private static final Set<String> REQUESTED_OUTPUTS = new HashSet<>();
    private static final Set<Identifier> REQUESTED_GROUP_TYPES = new HashSet<>();
    private static final Set<Identifier> COMPLETE_GROUP_TYPES = new HashSet<>();
    private static volatile IJeiRuntime runtime;
    private static boolean recipesDirty;

    private JeiCatalystIndex() {}

    /** Runtime startup only records category metadata. Recipe layouts are materialized on demand. */
    public static void rebuild(IJeiRuntime runtime)
    {
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        SEARCH_QUEUE.clear();
        GROUP_QUEUE.clear();
        REQUESTED_OUTPUTS.clear();
        REQUESTED_GROUP_TYPES.clear();
        COMPLETE_GROUP_TYPES.clear();
        RECIPES_BY_ID.clear();
        recipesDirty = false;
        RecipeCatalog.clearClient();
        Map<Identifier, LinkedHashSet<Identifier>> building = new HashMap<>();
        Map<Identifier, Component> titles = new HashMap<>();
        Map<Identifier, IRecipeCategory<?>> categories = new HashMap<>();
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            try
            {
                var recipeType = category.getRecipeType();
                Identifier typeId = recipeType.getUid();
                categories.put(typeId, category);
                titles.put(typeId, category.getTitle());
                manager.createCraftingStationLookup(recipeType).includeHidden().getItemStack()
                        .filter(stack -> !stack.isEmpty())
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .forEach(item -> building.computeIfAbsent(item, ignored ->
                                new LinkedHashSet<>()).add(typeId));
            }
            catch (RuntimeException | LinkageError exception)
            {
                LOGGER.warn("Unable to index JEI recipe category {}", category.getClass().getName(), exception);
            }
        });
        Map<Identifier, Set<Identifier>> frozen = new HashMap<>();
        building.forEach((item, types) -> frozen.put(item, Set.copyOf(types)));
        TYPES_BY_CATALYST = Map.copyOf(frozen);
        TITLES_BY_TYPE = Map.copyOf(titles);
        CATEGORIES_BY_TYPE = Map.copyOf(categories);
        INPUT_GROUPS_BY_TYPE = Map.of();
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    public static boolean requestRecipesFor(IStackKey<?> output)
    {
        IJeiRuntime current = runtime;
        if (current == null || output == null || output.isEmpty()) return false;
        String token = RecipeResourceResolver.sortKey(output);
        if (!REQUESTED_OUTPUTS.add(token)) return true;
        var typed = current.getIngredientManager().createTypedIngredient(output.getReadOnlyStack(), false);
        if (typed.isEmpty()) return false;
        IFocus<?> focus = current.getJeiHelpers().getFocusFactory().createFocus(
                mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, typed.get());
        CATEGORIES_BY_TYPE.values().forEach(category -> SEARCH_QUEUE.addLast(new SearchTask(category, focus)));
        return true;
    }

    public static void requestInputGroupsFor(Set<Identifier> types)
    {
        for (Identifier type : types)
        {
            if (!REQUESTED_GROUP_TYPES.add(type)) continue;
            IRecipeCategory<?> category = CATEGORIES_BY_TYPE.get(type);
            if (category == null) COMPLETE_GROUP_TYPES.add(type);
            else GROUP_QUEUE.addLast(new SearchTask(category, type));
        }
    }

    public static boolean inputGroupsReady(Set<Identifier> types)
    { return COMPLETE_GROUP_TYPES.containsAll(types); }

    public static void tick()
    {
        IJeiRuntime current = runtime;
        if (current == null) return;
        int remaining = Math.min(MAX_LAYOUTS_PER_FRAME, CraftlinesConfig.RECIPE_INDEX_MAX_PER_TICK.get());
        long deadline = System.nanoTime() + MAX_INDEX_NANOS_PER_FRAME;
        while (remaining > 0 && (!SEARCH_QUEUE.isEmpty() || !GROUP_QUEUE.isEmpty())
                && System.nanoTime() < deadline)
        {
            ArrayDeque<SearchTask> queue = SEARCH_QUEUE.isEmpty() ? GROUP_QUEUE : SEARCH_QUEUE;
            SearchTask task = queue.peekFirst();
            boolean processed = task.advance(current);
            if (task.complete())
            {
                queue.removeFirst();
                if (task.groupType() != null) COMPLETE_GROUP_TYPES.add(task.groupType());
            }
            if (processed) remaining--;
        }
        if (SEARCH_QUEUE.isEmpty() && recipesDirty)
        {
            recipesDirty = false;
            RecipeCatalog.setClientRecipes(RECIPES_BY_ID.values());
        }
    }

    public static boolean idle() { return SEARCH_QUEUE.isEmpty() && !recipesDirty; }

    public static Set<Identifier> recipeTypesFor(ItemStack catalyst)
    {
        return catalyst.isEmpty() ? Set.of()
                : TYPES_BY_CATALYST.getOrDefault(BuiltInRegistries.ITEM.getKey(catalyst.getItem()), Set.of());
    }

    public static Optional<Component> recipeTypeTitle(Identifier type)
    { return Optional.ofNullable(TITLES_BY_TYPE.get(type)); }

    public static Set<Identifier> recipeTypes() { return TITLES_BY_TYPE.keySet(); }

    public static Map<Identifier, Set<String>> inputGroupsFor(Set<Identifier> types)
    {
        Map<Identifier, Set<String>> result = new HashMap<>();
        types.forEach(type -> result.put(type, INPUT_GROUPS_BY_TYPE.getOrDefault(type, Set.of())));
        return Map.copyOf(result);
    }

    public static Set<Identifier> recipeTypes(Set<String> loadedFamilies,
                                              Map<String, Set<String>> aliases,
                                              boolean debugMappings)
    { return recipeTypes(); }

    public static void clear()
    {
        runtime = null;
        SEARCH_QUEUE.clear();
        GROUP_QUEUE.clear();
        REQUESTED_OUTPUTS.clear();
        REQUESTED_GROUP_TYPES.clear();
        COMPLETE_GROUP_TYPES.clear();
        RECIPES_BY_ID.clear();
        recipesDirty = false;
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        INPUT_GROUPS_BY_TYPE = Map.of();
        CATEGORIES_BY_TYPE = Map.of();
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        RecipeCatalog.clearClient();
    }

    private static void captured(Identifier type, IRecipeCategory<Object> category,
                                 Object displayedRecipe, JeiVirtualRecipeLayouts.Captured captured,
                                 boolean recursive)
    {
        if (recursive) rememberServerRecipe(category, displayedRecipe);
        if (recursive && !JeiRecipeExecutionSource.usesServerRecipe(displayedRecipe))
            JeiVirtualRecipeLayouts.register(captured);
        mergeInputGroups(type, captured.inputs().stream().map(
                com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload.VirtualInput::inputGroup)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        if (recursive)
            for (var input : captured.inputs())
                for (var candidate : input.candidates()) requestRecipesFor(candidate.key());
    }

    private static void rememberServerRecipe(IRecipeCategory<Object> category, Object displayedRecipe)
    {
        RecipeHolder<?> holder = null;
        if (displayedRecipe instanceof RecipeHolder<?> value) holder = value;
        else if (displayedRecipe instanceof Recipe<?> recipe)
        {
            Identifier id = category.getIdentifier(displayedRecipe);
            if (id != null) holder = holder(id, recipe);
        }
        if (holder != null && RECIPES_BY_ID.putIfAbsent(holder.id().identifier(), holder) == null)
            recipesDirty = true;
    }

    private static void mergeInputGroups(Identifier type, Set<String> discovered)
    {
        if (discovered.isEmpty()) return;
        Map<Identifier, Set<String>> updated = new HashMap<>(INPUT_GROUPS_BY_TYPE);
        LinkedHashSet<String> groups = new LinkedHashSet<>(updated.getOrDefault(type, Set.of()));
        if (!groups.addAll(discovered)) return;
        updated.put(type, Set.copyOf(groups));
        INPUT_GROUPS_BY_TYPE = Map.copyOf(updated);
    }

    private static <R extends Recipe<?>> RecipeHolder<R> holder(Identifier id, R recipe)
    { return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe); }

    private static final class SearchTask
    {
        private final IRecipeCategory<Object> category;
        private final IFocus<?> focus;
        private final Identifier groupType;
        private Iterator<Object> recipes;
        private boolean complete;

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, IFocus<?> focus)
        { this.category = (IRecipeCategory<Object>) category; this.focus = focus; this.groupType = null; }

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, Identifier groupType)
        { this.category = (IRecipeCategory<Object>) category; this.focus = null; this.groupType = groupType; }

        private boolean advance(IJeiRuntime runtime)
        {
            if (complete) return false;
            try
            {
                if (recipes == null)
                {
                    var lookup = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                            .includeHidden();
                    if (focus != null) lookup.limitFocus(List.of(focus));
                    recipes = lookup.get().iterator();
                }
                if (!recipes.hasNext()) { complete = true; return false; }
                Object recipe = recipes.next();
                var focuses = focus == null ? runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()
                        : runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus));
                runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe, focuses)
                        .ifPresent(layout -> {
                            var value = JeiVirtualRecipeLayouts.capture(category.getRecipeType().getUid(), layout);
                            if (value != null) captured(category.getRecipeType().getUid(), category, recipe, value,
                                    groupType == null);
                        });
                if (!recipes.hasNext()) complete = true;
                return true;
            }
            catch (RuntimeException | LinkageError exception)
            {
                complete = true;
                LOGGER.warn("Unable to lazily index JEI recipe category {}", category.getClass().getName(), exception);
                return false;
            }
        }

        private boolean complete() { return complete; }
        private Identifier groupType() { return groupType; }
    }
}
