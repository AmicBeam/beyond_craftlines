package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalog;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeTypeWarmupTracker;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final ArrayDeque<SearchTask> TYPE_QUEUE = new ArrayDeque<>();
    private static final RecipeTypeWarmupTracker<Identifier> TYPE_STATE = new RecipeTypeWarmupTracker<>();
    private static volatile IJeiRuntime runtime;
    private static boolean recipesDirty;

    private JeiCatalystIndex() {}

    /** Runtime startup only records category metadata. Recipe layouts are materialized on demand. */
    public static void rebuild(IJeiRuntime runtime)
    {
        Set<Identifier> previousActiveTypes = TYPE_STATE.activeTypes();
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.reload(
                net.minecraft.client.Minecraft.getInstance().getResourceManager());
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
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
        enqueueRecipeTypes(TYPE_STATE.activate(previousActiveTypes.stream()
                .filter(CATEGORIES_BY_TYPE::containsKey).toList()));
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    public static void prewarmRecipeTypes(java.util.Collection<String> types)
    {
        Set<Identifier> parsed = knownRecipeTypes(types);
        enqueueRecipeTypes(TYPE_STATE.activate(parsed));
    }

    public static Set<String> rematerializeRecipeTypes(java.util.Collection<String> types)
    {
        Set<Identifier> parsed = types.stream().map(JeiCatalystIndex::recipeTypeId)
                .filter(java.util.Objects::nonNull).filter(CATEGORIES_BY_TYPE::containsKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        enqueueRecipeTypes(TYPE_STATE.restart(parsed));
        return parsed.stream().map(Object::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static boolean recipeTypesReady(java.util.Collection<String> types)
    { return runtime == null || TYPE_STATE.ready(knownRecipeTypes(types)); }

    private static Set<Identifier> knownRecipeTypes(java.util.Collection<String> types)
    {
        LinkedHashSet<Identifier> parsed = new LinkedHashSet<>();
        for (String value : types)
        {
            Identifier type = recipeTypeId(value);
            if (type != null && CATEGORIES_BY_TYPE.containsKey(type)) parsed.add(type);
        }
        Identifier crafting = Identifier.fromNamespaceAndPath("minecraft", "crafting");
        if (CATEGORIES_BY_TYPE.containsKey(crafting)) parsed.add(crafting);
        return parsed;
    }

    private static Identifier recipeTypeId(String value)
    {
        if (value == null || value.isBlank()) return null;
        if ("crafting".equals(value)) return Identifier.fromNamespaceAndPath("minecraft", "crafting");
        Identifier parsed = Identifier.tryParse(value);
        if (parsed != null) return parsed;
        return Identifier.tryParse("minecraft:" + value);
    }

    private static void requestRecipeTypes(java.util.Collection<Identifier> types)
    { enqueueRecipeTypes(TYPE_STATE.request(types)); }

    private static void enqueueRecipeTypes(java.util.Collection<Identifier> types)
    {
        for (Identifier type : types)
        {
            IRecipeCategory<?> category = CATEGORIES_BY_TYPE.get(type);
            if (category == null) TYPE_STATE.complete(type);
            else TYPE_QUEUE.addLast(new SearchTask(category, type));
        }
    }

    public static boolean requestRecipesFor(IStackKey<?> output)
    {
        if (runtime == null || output == null || output.isEmpty()) return false;
        Set<Identifier> activeTypes = TYPE_STATE.activeTypes();
        if (activeTypes.isEmpty())
        {
            enqueueRecipeTypes(TYPE_STATE.activate(CATEGORIES_BY_TYPE.keySet()));
        }
        else requestRecipeTypes(activeTypes);
        return true;
    }

    public static void requestInputGroupsFor(Set<Identifier> types)
    {
        enqueueRecipeTypes(TYPE_STATE.activate(types));
    }

    public static boolean inputGroupsReady(Set<Identifier> types)
    { return TYPE_STATE.ready(types); }

    public static void tick()
    {
        IJeiRuntime current = runtime;
        if (current == null) return;
        int remaining = Math.min(MAX_LAYOUTS_PER_FRAME, CraftlinesConfig.RECIPE_INDEX_MAX_PER_TICK.get());
        long deadline = System.nanoTime() + MAX_INDEX_NANOS_PER_FRAME;
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
        if (TYPE_QUEUE.isEmpty() && recipesDirty)
        {
            recipesDirty = false;
            RecipeCatalog.setClientRecipes(RECIPES_BY_ID.values());
        }
    }

    public static boolean idle() { return TYPE_QUEUE.isEmpty() && !recipesDirty; }

    public static Set<Identifier> recipeTypesFor(ItemStack catalyst)
    {
        if (catalyst.isEmpty()) return Set.of();
        LinkedHashSet<Identifier> result = new LinkedHashSet<>(TYPES_BY_CATALYST.getOrDefault(
                BuiltInRegistries.ITEM.getKey(catalyst.getItem()), Set.of()));
        result.addAll(com.amicbeam.beyondcraftlines.client.integration.emi.EmiOptionalIntegration
                .recipeTypesFor(catalyst));
        return Set.copyOf(result);
    }

    public static Optional<Component> recipeTypeTitle(Identifier type)
    {
        Optional<Component> emi = com.amicbeam.beyondcraftlines.client.integration.emi
                .EmiOptionalIntegration.recipeTypeTitle(type);
        return emi.isPresent() ? emi : Optional.ofNullable(TITLES_BY_TYPE.get(type));
    }

    public static Set<Identifier> recipeTypes()
    {
        LinkedHashSet<Identifier> result = new LinkedHashSet<>(TITLES_BY_TYPE.keySet());
        result.addAll(com.amicbeam.beyondcraftlines.client.integration.emi.EmiOptionalIntegration
                .recipeTypes());
        return Set.copyOf(result);
    }

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
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.clear();
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
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
                                 Object displayedRecipe, JeiVirtualRecipeLayouts.Captured captured)
    {
        rememberServerRecipe(category, displayedRecipe);
        if (!JeiRecipeExecutionSource.usesServerRecipe(displayedRecipe))
            JeiVirtualRecipeLayouts.register(captured);
        mergeInputGroups(type, captured.inputs().stream().map(
                com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload.VirtualInput::inputGroup)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
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
        private final Identifier recipeType;
        private Iterator<Object> recipes;
        private boolean complete;

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, Identifier recipeType)
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
                            var value = JeiVirtualRecipeLayouts.capture(category.getRecipeType().getUid(), layout);
                            if (value != null) captured(category.getRecipeType().getUid(), category, recipe, value);
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
        private Identifier recipeType() { return recipeType; }
    }
}
