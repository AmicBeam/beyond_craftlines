package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    private static volatile Map<Item, Set<ResourceLocation>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<ResourceLocation, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<ResourceLocation, Set<String>> INPUT_GROUPS_BY_TYPE = Map.of();
    private static volatile Map<ResourceLocation, IRecipeCategory<?>> CATEGORIES_BY_TYPE = Map.of();
    private static final ArrayDeque<SearchTask> SEARCH_QUEUE = new ArrayDeque<>();
    private static final ArrayDeque<SearchTask> GROUP_QUEUE = new ArrayDeque<>();
    private static final Set<String> REQUESTED_OUTPUTS = new HashSet<>();
    private static final Set<ResourceLocation> REQUESTED_GROUP_TYPES = new HashSet<>();
    private static final Set<ResourceLocation> COMPLETE_GROUP_TYPES = new HashSet<>();
    private static volatile IJeiRuntime runtime;

    private JeiCatalystIndex() {}

    /** Runtime startup only records category metadata. Recipe layouts are materialized on demand. */
    public static void rebuild(IJeiRuntime runtime)
    {
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.reload(
                net.minecraft.client.Minecraft.getInstance().getResourceManager());
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        SEARCH_QUEUE.clear();
        GROUP_QUEUE.clear();
        REQUESTED_OUTPUTS.clear();
        REQUESTED_GROUP_TYPES.clear();
        COMPLETE_GROUP_TYPES.clear();
        Map<Item, LinkedHashSet<ResourceLocation>> building = new HashMap<>();
        Map<ResourceLocation, Component> titles = new HashMap<>();
        Map<ResourceLocation, IRecipeCategory<?>> categories = new HashMap<>();
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            try
            {
                var recipeType = category.getRecipeType();
                ResourceLocation typeId = recipeType.getUid();
                categories.put(typeId, category);
                titles.put(typeId, category.getTitle());
                manager.createRecipeCatalystLookup(recipeType).includeHidden().getItemStack()
                        .filter(stack -> !stack.isEmpty()).map(ItemStack::getItem)
                        .forEach(item -> building.computeIfAbsent(item, ignored ->
                                new LinkedHashSet<>()).add(typeId));
            }
            catch (RuntimeException | LinkageError exception)
            {
                LOGGER.warn("Unable to index JEI recipe category {}", category.getClass().getName(), exception);
            }
        });
        Map<Item, Set<ResourceLocation>> frozen = new HashMap<>();
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

    /** Adds a recursive, output-focused JEI search without creating layouts on the caller's frame. */
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

    /** Materializes only explicitly requested machine categories to discover their input groups. */
    public static void requestInputGroupsFor(Set<ResourceLocation> types)
    {
        for (ResourceLocation type : types)
        {
            if (!REQUESTED_GROUP_TYPES.add(type)) continue;
            IRecipeCategory<?> category = CATEGORIES_BY_TYPE.get(type);
            if (category == null) COMPLETE_GROUP_TYPES.add(type);
            else GROUP_QUEUE.addLast(new SearchTask(category, type));
        }
    }

    public static boolean inputGroupsReady(Set<ResourceLocation> types)
    { return COMPLETE_GROUP_TYPES.containsAll(types); }

    /** Runs from the client render path with both a count and a wall-clock budget. */
    public static void tick()
    {
        IJeiRuntime current = runtime;
        if (current == null || SEARCH_QUEUE.isEmpty() && GROUP_QUEUE.isEmpty()) return;
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
    }

    public static boolean idle() { return SEARCH_QUEUE.isEmpty(); }

    public static Set<ResourceLocation> recipeTypesFor(ItemStack catalyst)
    { return catalyst.isEmpty() ? Set.of() : TYPES_BY_CATALYST.getOrDefault(catalyst.getItem(), Set.of()); }

    public static Optional<Component> recipeTypeTitle(ResourceLocation type)
    { return Optional.ofNullable(TITLES_BY_TYPE.get(type)); }

    public static Set<ResourceLocation> recipeTypes() { return TITLES_BY_TYPE.keySet(); }

    public static Map<ResourceLocation, Set<String>> inputGroupsFor(Set<ResourceLocation> types)
    {
        Map<ResourceLocation, Set<String>> result = new HashMap<>();
        types.forEach(type -> result.put(type, INPUT_GROUPS_BY_TYPE.getOrDefault(type, Set.of())));
        return Map.copyOf(result);
    }

    public static Set<ResourceLocation> recipeTypes(Set<String> loadedFamilies,
                                                    Map<String, Set<String>> aliases,
                                                    boolean debugMappings)
    { return recipeTypes(); }

    public static void clear()
    {
        runtime = null;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.clear();
        SEARCH_QUEUE.clear();
        GROUP_QUEUE.clear();
        REQUESTED_OUTPUTS.clear();
        REQUESTED_GROUP_TYPES.clear();
        COMPLETE_GROUP_TYPES.clear();
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        INPUT_GROUPS_BY_TYPE = Map.of();
        CATEGORIES_BY_TYPE = Map.of();
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
    }

    private static void captured(ResourceLocation type, Object displayedRecipe,
                                 JeiVirtualRecipeLayouts.Captured captured, boolean recursive)
    {
        if (recursive && !JeiRecipeExecutionSource.usesServerRecipe(displayedRecipe))
            JeiVirtualRecipeLayouts.register(captured);
        mergeInputGroups(type, captured.inputs().stream().map(
                com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload.VirtualInput::inputGroup)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        if (recursive)
            for (var input : captured.inputs())
                for (var candidate : input.candidates()) requestRecipesFor(candidate.key());
    }

    private static void mergeInputGroups(ResourceLocation type, Set<String> discovered)
    {
        if (discovered.isEmpty()) return;
        Map<ResourceLocation, Set<String>> updated = new HashMap<>(INPUT_GROUPS_BY_TYPE);
        LinkedHashSet<String> groups = new LinkedHashSet<>(updated.getOrDefault(type, Set.of()));
        if (!groups.addAll(discovered)) return;
        updated.put(type, Set.copyOf(groups));
        INPUT_GROUPS_BY_TYPE = Map.copyOf(updated);
    }

    private static final class SearchTask
    {
        private final IRecipeCategory<Object> category;
        private final IFocus<?> focus;
        private final ResourceLocation groupType;
        private Iterator<Object> recipes;
        private boolean complete;

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, IFocus<?> focus)
        { this.category = (IRecipeCategory<Object>) category; this.focus = focus; this.groupType = null; }

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, ResourceLocation groupType)
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
                            if (value != null) captured(category.getRecipeType().getUid(), recipe, value,
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
        private ResourceLocation groupType() { return groupType; }
    }
}
