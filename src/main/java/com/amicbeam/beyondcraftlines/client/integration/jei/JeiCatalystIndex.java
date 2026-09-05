package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeTypeWarmupTracker;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lightweight JEI metadata plus a target-driven, frame-budgeted virtual recipe index. */
public final class JeiCatalystIndex
{
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final int MAX_LAYOUTS_PER_FRAME = 32;
    private static volatile Map<Item, Set<ResourceLocation>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<ResourceLocation, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<ResourceLocation, Set<String>> INPUT_GROUPS_BY_TYPE = Map.of();
    private static volatile Map<ResourceLocation, IRecipeCategory<?>> CATEGORIES_BY_TYPE = Map.of();
    private static final ArrayDeque<SearchTask> TYPE_QUEUE = new ArrayDeque<>();
    private static final RecipeTypeWarmupTracker<ResourceLocation> TYPE_STATE = new RecipeTypeWarmupTracker<>();
    private static volatile IJeiRuntime runtime;

    private JeiCatalystIndex() {}

    /** Runtime startup only records category metadata. Recipe layouts are materialized on demand. */
    public static void rebuild(IJeiRuntime runtime)
    {
        Set<ResourceLocation> previousActiveTypes = TYPE_STATE.activeTypes();
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.reload(
                net.minecraft.client.Minecraft.getInstance().getResourceManager());
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
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
        enqueueRecipeTypes(TYPE_STATE.activate(previousActiveTypes.stream()
                .filter(CATEGORIES_BY_TYPE::containsKey).toList()));
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    /** Prewarms each enabled JEI category once for the current runtime generation. */
    public static void prewarmRecipeTypes(java.util.Collection<String> types)
    {
        Set<ResourceLocation> parsed = knownRecipeTypes(types);
        enqueueRecipeTypes(TYPE_STATE.activate(parsed));
    }

    public static boolean recipeTypesReady(java.util.Collection<String> types)
    { return runtime == null || TYPE_STATE.ready(knownRecipeTypes(types)); }

    public static int completedRecipeTypes(java.util.Collection<String> types)
    { return TYPE_STATE.completedCount(knownRecipeTypes(types)); }

    public static int totalRecipeTypes(java.util.Collection<String> types)
    { return knownRecipeTypes(types).size(); }

    private static Set<ResourceLocation> knownRecipeTypes(java.util.Collection<String> types)
    {
        LinkedHashSet<ResourceLocation> parsed = new LinkedHashSet<>();
        for (String value : types)
        {
            ResourceLocation type = recipeTypeId(value);
            if (type != null && CATEGORIES_BY_TYPE.containsKey(type)) parsed.add(type);
        }
        ResourceLocation crafting = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
        if (CATEGORIES_BY_TYPE.containsKey(crafting)) parsed.add(crafting);
        return parsed;
    }

    private static ResourceLocation recipeTypeId(String value)
    {
        if (value == null || value.isBlank()) return null;
        if ("crafting".equals(value)) return ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed != null) return parsed;
        return ResourceLocation.tryParse("minecraft:" + value);
    }

    private static void requestRecipeTypes(java.util.Collection<ResourceLocation> types)
    { enqueueRecipeTypes(TYPE_STATE.request(types)); }

    private static void enqueueRecipeTypes(java.util.Collection<ResourceLocation> types)
    {
        for (ResourceLocation type : types)
        {
            IRecipeCategory<?> category = CATEGORIES_BY_TYPE.get(type);
            if (category == null) TYPE_STATE.complete(type);
            else TYPE_QUEUE.addLast(new SearchTask(category, type));
        }
    }

    /** Ensures the active network's type catalog is ready before opening an order tree. */
    public static boolean requestRecipesFor(IStackKey<?> output)
    {
        if (runtime == null || output == null || output.isEmpty()) return false;
        Set<ResourceLocation> activeTypes = TYPE_STATE.activeTypes();
        if (activeTypes.isEmpty())
        {
            enqueueRecipeTypes(TYPE_STATE.activate(CATEGORIES_BY_TYPE.keySet()));
        }
        else requestRecipeTypes(activeTypes);
        return true;
    }

    /** The same per-type warmup also discovers stable input groups. */
    public static void requestInputGroupsFor(Set<ResourceLocation> types)
    {
        enqueueRecipeTypes(TYPE_STATE.activate(types));
    }

    public static boolean inputGroupsReady(Set<ResourceLocation> types)
    { return TYPE_STATE.ready(types); }

    /** Runs from the client render path with both a count and a wall-clock budget. */
    public static void tick(long timeBudgetNanos)
    {
        IJeiRuntime current = runtime;
        if (current == null || TYPE_QUEUE.isEmpty() || timeBudgetNanos < 1) return;
        int remaining = MAX_LAYOUTS_PER_FRAME;
        long deadline = System.nanoTime() + timeBudgetNanos;
        while (remaining > 0 && !TYPE_QUEUE.isEmpty() && System.nanoTime() < deadline)
        {
            SearchTask task = TYPE_QUEUE.peekFirst();
            boolean processed = task.advance(current);
            if (task.complete())
            {
                TYPE_QUEUE.removeFirst();
                TYPE_STATE.complete(task.recipeType());
            }
            if (processed) remaining--;
        }
    }

    public static boolean idle() { return TYPE_QUEUE.isEmpty(); }

    public static Set<ResourceLocation> recipeTypesFor(ItemStack catalyst)
    {
        if (catalyst.isEmpty()) return Set.of();
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>(
                TYPES_BY_CATALYST.getOrDefault(catalyst.getItem(), Set.of()));
        result.addAll(com.amicbeam.beyondcraftlines.client.integration.emi.EmiOptionalIntegration
                .recipeTypesFor(catalyst));
        return Set.copyOf(result);
    }

    public static Optional<Component> recipeTypeTitle(ResourceLocation type)
    {
        Optional<Component> emi = com.amicbeam.beyondcraftlines.client.integration.emi
                .EmiOptionalIntegration.recipeTypeTitle(type);
        return emi.isPresent() ? emi : Optional.ofNullable(TITLES_BY_TYPE.get(type));
    }

    public static Set<ResourceLocation> recipeTypes()
    {
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>(TITLES_BY_TYPE.keySet());
        result.addAll(com.amicbeam.beyondcraftlines.client.integration.emi.EmiOptionalIntegration
                .recipeTypes());
        return Set.copyOf(result);
    }

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
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
        TYPES_BY_CATALYST = Map.of();
        TITLES_BY_TYPE = Map.of();
        INPUT_GROUPS_BY_TYPE = Map.of();
        CATEGORIES_BY_TYPE = Map.of();
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
    }

    private static void captured(ResourceLocation type, Object displayedRecipe,
                                 java.util.List<JeiVirtualRecipeLayouts.Captured> captures)
    {
        if (!JeiRecipeExecutionSource.usesServerRecipe(displayedRecipe))
            captures.forEach(JeiVirtualRecipeLayouts::register);
        mergeInputGroups(type, captures.stream().flatMap(captured -> captured.inputs().stream()).map(
                com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload.VirtualInput::inputGroup)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
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
        private final ResourceLocation recipeType;
        private Iterator<Object> recipes;
        private boolean complete;

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, ResourceLocation recipeType)
        { this.category = (IRecipeCategory<Object>) category; this.recipeType = recipeType; }

        private boolean advance(IJeiRuntime runtime)
        {
            if (complete) return false;
            try
            {
                if (recipes == null)
                {
                    var lookup = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                            .includeHidden();
                    recipes = lookup.get().iterator();
                }
                if (!recipes.hasNext()) { complete = true; return false; }
                Object recipe = recipes.next();
                runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
                        .ifPresent(layout -> {
                            var values = JeiVirtualRecipeLayouts.captures(category.getRecipeType().getUid(), layout);
                            if (!values.isEmpty())
                                JeiCatalystIndex.captured(category.getRecipeType().getUid(), recipe, values);
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
        private ResourceLocation recipeType() { return recipeType; }
    }
}
