package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalog;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeTypeWarmupTracker;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeIndexDiagnostics;
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
    private static volatile Map<Identifier, Set<Identifier>> TYPES_BY_CATALYST = Map.of();
    private static volatile Map<Identifier, Component> TITLES_BY_TYPE = Map.of();
    private static volatile Map<Identifier, Set<String>> INPUT_GROUPS_BY_TYPE = Map.of();
    private static volatile Map<Identifier, IRecipeCategory<?>> CATEGORIES_BY_TYPE = Map.of();
    private static final Map<Identifier, RecipeHolder<?>> RECIPES_BY_ID = new LinkedHashMap<>();
    private static final ArrayDeque<SearchTask> TYPE_QUEUE = new ArrayDeque<>();
    private static final RecipeTypeWarmupTracker<Identifier> TYPE_STATE = new RecipeTypeWarmupTracker<>();
    private static volatile IJeiRuntime runtime;
    private static volatile Set<String> allPlanningFamilies = Set.of();
    private static boolean allTypesRequested;
    private static boolean recipesDirty;

    private JeiCatalystIndex() {}

    /** Runtime startup only records category metadata. Recipe layouts are materialized on demand. */
    public static void rebuild(IJeiRuntime runtime)
    {
        com.amicbeam.beyondcraftlines.client.ClientPlanningCatalogWarmup.invalidate();
        JeiVirtualRecipeLayouts.resetDiagnostics();
        RecipeIndexDiagnostics.reset();
        Set<Identifier> previousActiveTypes = TYPE_STATE.activeTypes();
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.reload(
                net.minecraft.client.Minecraft.getInstance().getResourceManager());
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
        allTypesRequested = false;
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
        allPlanningFamilies = com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalogScope.fullFamilies(
                categories.keySet().stream().map(Object::toString).toList());
        INPUT_GROUPS_BY_TYPE = Map.of();
        enqueueRecipeTypes(TYPE_STATE.activate(previousActiveTypes.stream()
                .filter(CATEGORIES_BY_TYPE::containsKey).toList()));
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    /** Unchanged recipe-viewer metadata must not discard completed recipe layouts. */
    public static void refreshIfCategoriesChanged()
    {
        IJeiRuntime current = runtime;
        if (current == null) return;
        var categories = current.getRecipeManager().createRecipeCategoryLookup()
                .includeHidden().get().collect(java.util.stream.Collectors.toMap(
                        category -> category.getRecipeType().getUid(), category -> category));
        if (!categories.equals(CATEGORIES_BY_TYPE)) rebuild(current);
    }

    public static void prewarmRecipeTypes(java.util.Collection<String> types)
    {
        Set<Identifier> parsed = knownRecipeTypes(types);
        enqueueRecipeTypes(TYPE_STATE.activate(parsed));
    }

    public static Set<String> planningFamilies(java.util.Collection<String> networkFamilies)
    {
        return com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalogScope.select(
                com.amicbeam.beyondcraftlines.CraftlinesConfig.PRELOAD_ALL_RECIPE_TYPES.get(),
                networkFamilies, allPlanningFamilies);
    }

    /** Once per runtime; it does not depend on having a network or bound machines. */
    public static void prewarmAllRecipeTypes()
    {
        if (runtime == null || allTypesRequested) return;
        allTypesRequested = true;
        enqueueRecipeTypes(TYPE_STATE.activate(CATEGORIES_BY_TYPE.keySet()));
    }

    public static boolean recipeTypesReady(java.util.Collection<String> types)
    { return runtime == null || TYPE_STATE.ready(knownRecipeTypes(types)); }

    public static int completedRecipeTypes(java.util.Collection<String> types)
    { return TYPE_STATE.completedCount(knownRecipeTypes(types)); }

    public static int totalRecipeTypes(java.util.Collection<String> types)
    { return knownRecipeTypes(types).size(); }

    private static Set<Identifier> knownRecipeTypes(java.util.Collection<String> types)
    {
        if (com.amicbeam.beyondcraftlines.CraftlinesConfig.PRELOAD_ALL_RECIPE_TYPES.get())
            return CATEGORIES_BY_TYPE.keySet();
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

    public static void tick(long timeBudgetNanos)
    {
        IJeiRuntime current = runtime;
        if (current == null) return;
        int remaining = MAX_LAYOUTS_PER_FRAME;
        long deadline=System.nanoTime()+Math.max(0L,timeBudgetNanos);
        while (remaining > 0 && !TYPE_QUEUE.isEmpty() && System.nanoTime() < deadline)
        {
            SearchTask task = TYPE_QUEUE.peekFirst();
            boolean processed = task.advance(current);
            if (task.complete())
            {
                TYPE_QUEUE.removeFirst();
                TYPE_STATE.complete(task.recipeType());
                if (TYPE_QUEUE.isEmpty()) RecipeIndexDiagnostics.summarize("jei_warmup_complete");
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
        RecipeIndexDiagnostics.reset();
        runtime = null;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.clear();
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
        allTypesRequested = false;
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
                                 Object displayedRecipe,
                                 java.util.List<JeiVirtualRecipeLayouts.Captured> captures)
    {
        rememberServerRecipe(category, displayedRecipe);
        if (!JeiRecipeExecutionSource.usesServerRecipe(displayedRecipe))
            captures.forEach(JeiVirtualRecipeLayouts::register);
        mergeInputGroups(type, captures.stream().flatMap(captured -> captured.inputs().stream()).map(
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
        private long recipeOrdinal;
        private String diagnosticId;

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, Identifier recipeType)
        { this.category = (IRecipeCategory<Object>) category; this.recipeType = recipeType; }

        private boolean advance(IJeiRuntime runtime)
        {
            if (complete) return false;
            Object recipe = null;
            try
            {
                long started = System.nanoTime();
                try
                {
                    if (recipes == null)
                    {
                        var lookup = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                                .includeHidden();
                        recipes = lookup.get().iterator();
                    }
                    if (!recipes.hasNext()) { complete = true; return false; }
                    recipe = recipes.next();
                    recipeOrdinal++;
                    diagnosticId = null;
                }
                finally { logSlow("jei_lookup", System.nanoTime() - started, recipe, java.util.List.of()); }

                java.util.Optional<mezz.jei.api.gui.IRecipeLayoutDrawable<Object>> layout;
                started = System.nanoTime();
                try
                {
                    layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                            runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
                }
                finally { logSlow("jei_layout", System.nanoTime() - started, recipe, java.util.List.of()); }
                if (layout.isPresent())
                {
                    java.util.List<JeiVirtualRecipeLayouts.Captured> values = java.util.List.of();
                    started = System.nanoTime();
                    try { values = JeiVirtualRecipeLayouts.captures(recipeType, layout.get()); }
                    finally { logSlow("jei_capture", System.nanoTime() - started, recipe, values); }
                    if (!values.isEmpty())
                    {
                        started = System.nanoTime();
                        try { JeiCatalystIndex.captured(recipeType, category, recipe, values); }
                        finally { logSlow("jei_register", System.nanoTime() - started, recipe, values); }
                    }
                }
                started = System.nanoTime();
                try { if (!recipes.hasNext()) complete = true; }
                finally { logSlow("jei_lookup", System.nanoTime() - started, recipe, java.util.List.of()); }
                return true;
            }
            catch (RuntimeException | LinkageError exception)
            {
                complete = true;
                LOGGER.warn("Unable to lazily index JEI recipe category {}", category.getClass().getName(), exception);
                return false;
            }
        }

        private void logSlow(String stage, long elapsedNanos, Object recipe,
                             java.util.List<JeiVirtualRecipeLayouts.Captured> values)
        {
            if (elapsedNanos < RecipeIndexDiagnostics.SLOW_NANOS) return;
            if (recipe != null && diagnosticId == null)
            {
                diagnosticId = "unregistered#" + recipeOrdinal;
                try
                {
                    Object id = recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                            ? holder.id().identifier() : category.getIdentifier(recipe);
                    if (id != null) diagnosticId = id.toString();
                }
                catch (RuntimeException | LinkageError ignored) {}
            }
            Object source = recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                    ? holder.value() : recipe;
            int slots = values.isEmpty() ? -1 : values.get(0).inputs().size();
            long candidates = values.isEmpty() ? -1 : values.get(0).inputs().stream()
                    .mapToLong(input -> input.candidates().size()).sum();
            RecipeIndexDiagnostics.record(stage, elapsedNanos, recipe == null ? "<category>" : diagnosticId,
                    recipeType, source == null ? category.getClass() : source.getClass(), slots, candidates);
        }

        private boolean complete() { return complete; }
        private Identifier recipeType() { return recipeType; }
    }
}
