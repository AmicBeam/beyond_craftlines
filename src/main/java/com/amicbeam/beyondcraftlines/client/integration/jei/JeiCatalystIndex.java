package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeTypeWarmupTracker;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeIndexDiagnostics;
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
    private static volatile Set<String> allPlanningFamilies = Set.of();
    private static boolean allTypesRequested;
    private static final ArrayDeque<SourceTask> SOURCE_QUEUE = new ArrayDeque<>();
    private static Map<String, java.util.List<String>> categorySources = new HashMap<>();
    private static Map<String, java.util.List<String>> legacyCategorySources = new HashMap<>();
    private static final Map<Object, String> nativeSourceIds = new java.util.IdentityHashMap<>();
    private static final Set<String> nativeSourceIdSet = new java.util.HashSet<>();
    private static Iterator<net.minecraft.world.item.crafting.RecipeHolder<?>> nativeSourceRecipes;
    private static boolean sourcesStarted;
    private static boolean persistentReady;
    private static long sourceStartedNanos;
    private static long scannedSourceRecipes;
    private static long nextSourceMetricsNanos;
    private static long nextLayoutMetricsNanos;

    private JeiCatalystIndex() {}

    /** Runtime startup only records category metadata. Recipe layouts are materialized on demand. */
    public static void rebuild(IJeiRuntime runtime)
    {
        com.amicbeam.beyondcraftlines.client.ClientPlanningCatalogWarmup.invalidate();
        JeiVirtualRecipeLayouts.resetDiagnostics();
        RecipeIndexDiagnostics.reset();
        resetPersistentCache();
        Set<ResourceLocation> previousActiveTypes = TYPE_STATE.activeTypes();
        JeiCatalystIndex.runtime = runtime;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.reload(
                net.minecraft.client.Minecraft.getInstance().getResourceManager());
        com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry.clear();
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
        allTypesRequested = false;
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
        allPlanningFamilies = com.amicbeam.beyondcraftlines.common.crafting.RecipeCatalogScope.fullFamilies(
                categories.keySet().stream().map(Object::toString).toList(),
                com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry.nativeFallbackFamilies());
        INPUT_GROUPS_BY_TYPE = Map.of();
        enqueueRecipeTypes(TYPE_STATE.activate(previousActiveTypes.stream()
                .filter(CATEGORIES_BY_TYPE::containsKey).toList()));
    }

    public static void refresh()
    {
        IJeiRuntime current = runtime;
        if (current != null) rebuild(current);
    }

    /** EMI publishes multiple managers while baking; unchanged JEI categories need no reindex. */
    public static void refreshIfCategoriesChanged()
    {
        IJeiRuntime current = runtime;
        if (current == null) return;
        var categories = current.getRecipeManager().createRecipeCategoryLookup()
                .includeHidden().get().collect(java.util.stream.Collectors.toMap(
                        category -> category.getRecipeType().getUid(), category -> category));
        if (!categories.equals(CATEGORIES_BY_TYPE)) rebuild(current);
    }

    /** Prewarms each enabled JEI category once for the current runtime generation. */
    public static void prewarmRecipeTypes(java.util.Collection<String> types)
    {
        Set<ResourceLocation> parsed = knownRecipeTypes(types);
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

    private static Set<ResourceLocation> knownRecipeTypes(java.util.Collection<String> types)
    {
        if (com.amicbeam.beyondcraftlines.CraftlinesConfig.PRELOAD_ALL_RECIPE_TYPES.get())
            return CATEGORIES_BY_TYPE.keySet();
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
        if (!preparePersistentCache(timeBudgetNanos)) return;
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
                if (TYPE_QUEUE.isEmpty())
                {
                    RecipeIndexDiagnostics.summarize("jei_warmup_complete");
                    com.amicbeam.beyondcraftlines.client.ClientJeiRecipeCache.save(
                            TYPE_STATE.completedTypes().stream().map(Object::toString)
                                    .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                            INPUT_GROUPS_BY_TYPE.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                                    entry -> entry.getKey().toString(), Map.Entry::getValue)));
                }
            }
            if (processed) remaining--;
        }
        logLayoutProgress();
    }

    private static void resetPersistentCache()
    {
        com.amicbeam.beyondcraftlines.client.ClientJeiRecipeCache.reset();
        SOURCE_QUEUE.clear();
        categorySources = new HashMap<>();
        legacyCategorySources = new HashMap<>();
        nativeSourceIds.clear();
        nativeSourceIdSet.clear();
        nativeSourceRecipes = null;
        sourcesStarted = false;
        persistentReady = false;
        scannedSourceRecipes = 0L;
        nextSourceMetricsNanos = 0L;
        nextLayoutMetricsNanos = 0L;
    }

    public static boolean checkingRecipeSources()
    { return !persistentReady && (!sourcesStarted || !SOURCE_QUEUE.isEmpty()); }
    public static long scannedSourceRecipes() { return scannedSourceRecipes; }
    public static int completedSourceTypes()
    { return sourcesStarted ? CATEGORIES_BY_TYPE.size() - SOURCE_QUEUE.size() : 0; }
    public static int totalSourceTypes() { return CATEGORIES_BY_TYPE.size(); }

    private static void logLayoutProgress()
    {
        long now = System.nanoTime();
        if (TYPE_QUEUE.isEmpty() || now < nextLayoutMetricsNanos) return;
        nextLayoutMetricsNanos = now + 5_000_000_000L;
        SearchTask task = TYPE_QUEUE.peekFirst();
        LOGGER.info("{} client JEI materialization progress pendingTypes={} currentType={} recipesInType={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                TYPE_QUEUE.size(), task.recipeType(), task.recipeOrdinal);
    }

    /** Cheap source enumeration runs on the render thread; no drawable is created here. */
    private static boolean preparePersistentCache(long timeBudgetNanos)
    {
        if (persistentReady) return true;
        if (net.minecraft.client.Minecraft.getInstance().level == null) return false;
        if (!sourcesStarted)
        {
            sourcesStarted = true;
            sourceStartedNanos = System.nanoTime();
            nativeSourceRecipes = com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.allRecipes(
                    net.minecraft.client.Minecraft.getInstance().level).iterator();
            CATEGORIES_BY_TYPE.forEach((type, category) -> SOURCE_QUEUE.addLast(new SourceTask(type, category)));
            LOGGER.info("{} client JEI source scan started categories={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX, SOURCE_QUEUE.size());
        }
        long deadline = System.nanoTime() + timeBudgetNanos;
        while (nativeSourceRecipes != null && nativeSourceRecipes.hasNext() && System.nanoTime() < deadline)
        {
            var holder = nativeSourceRecipes.next();
            String id = holder.id().toString();
            nativeSourceIds.put(holder.value(), id);
            nativeSourceIdSet.add(id);
        }
        if (nativeSourceRecipes != null && nativeSourceRecipes.hasNext()) return false;
        nativeSourceRecipes = null;
        while (!SOURCE_QUEUE.isEmpty() && System.nanoTime() < deadline)
        {
            SourceTask source = SOURCE_QUEUE.peekFirst();
            source.advance(runtime);
            if (source.complete)
            {
                SOURCE_QUEUE.removeFirst();
                if (!source.failed)
                {
                    categorySources.put(source.type.toString(), java.util.List.copyOf(source.tokens));
                    legacyCategorySources.put(source.type.toString(), java.util.List.copyOf(source.legacyTokens));
                }
            }
        }
        if (!SOURCE_QUEUE.isEmpty())
        {
            long now = System.nanoTime();
            if (now >= nextSourceMetricsNanos)
            {
                nextSourceMetricsNanos = now + 5_000_000_000L;
                LOGGER.info("{} client JEI source scan progress categories={}/{} scannedRecipes={} currentType={} elapsedMs={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        completedSourceTypes(), totalSourceTypes(), scannedSourceRecipes,
                        SOURCE_QUEUE.peekFirst().type, (now - sourceStartedNanos) / 1_000_000L);
            }
            return false;
        }
        com.amicbeam.beyondcraftlines.client.ClientJeiRecipeCache.Restored restored =
                com.amicbeam.beyondcraftlines.client.ClientJeiRecipeCache.prepare(categorySources, legacyCategorySources);
        if (restored == null) return false;
        persistentReady = true;
        categorySources = Map.of();
        legacyCategorySources = Map.of();
        nativeSourceIds.clear();
        nativeSourceIdSet.clear();
        Set<ResourceLocation> restoredTypes = restored.types().stream().map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull).filter(CATEGORIES_BY_TYPE::containsKey)
                .collect(java.util.stream.Collectors.toSet());
        TYPE_STATE.request(restoredTypes);
        restoredTypes.forEach(TYPE_STATE::complete);
        TYPE_QUEUE.removeIf(task -> restoredTypes.contains(task.recipeType()));
        if (TYPE_QUEUE.isEmpty() && com.amicbeam.beyondcraftlines.client.ClientJeiRecipeCache.needsRewrite())
            com.amicbeam.beyondcraftlines.client.ClientJeiRecipeCache.save(restored.types(), restored.groups());
        restored.groups().forEach((type, groups) -> {
            ResourceLocation id = ResourceLocation.tryParse(type);
            if (id != null) mergeInputGroups(id, groups);
        });

        LOGGER.info("{} client JEI preparation ready restoredTypes={} pendingTypes={} elapsedMs={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                restoredTypes.size(), TYPE_QUEUE.size(), (System.nanoTime() - sourceStartedNanos) / 1_000_000L);
        return true;
    }

    private static final class SourceTask
    {
        private final ResourceLocation type;
        private final IRecipeCategory<Object> category;
        private final java.util.List<String> tokens = new java.util.ArrayList<>();
        private final java.util.List<String> legacyTokens = new java.util.ArrayList<>();
        private Iterator<Object> recipes;
        private boolean complete;
        private boolean failed;

        @SuppressWarnings("unchecked")
        SourceTask(ResourceLocation type, IRecipeCategory<?> category)
        {
            this.type = type;
            this.category = (IRecipeCategory<Object>) category;
            tokens.add("category:" + com.amicbeam.beyondcraftlines.client.JeiRecipeSourceFingerprint.stableClassName(category.getClass()));
            legacyTokens.add("category:" + category.getClass().getName());
        }

        void advance(IJeiRuntime runtime)
        {
            long started = System.nanoTime();
            try
            {
                if (recipes == null) recipes = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                        .includeHidden().get().iterator();
                if (!recipes.hasNext()) { complete = true; return; }
                Object recipe = recipes.next();
                scannedSourceRecipes++;

                Object legacyId = recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                        ? holder.id() : category.getRegistryName(recipe);
                String legacyClass = java.lang.reflect.Proxy.isProxyClass(recipe.getClass())
                        ? java.util.Arrays.stream(recipe.getClass().getInterfaces()).map(Class::getName).sorted()
                                .collect(java.util.stream.Collectors.joining(",")) : recipe.getClass().getName();
                legacyTokens.add(legacyId == null ? "class:" + legacyClass : "id:" + legacyId);
                // JEI presentation/bookmark IDs may include runtime identities. Only native IDs are authoritative.
                String id = recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                        ? (nativeSourceIdSet.contains(holder.id().toString()) ? holder.id().toString() : null) : nativeSourceIds.get(recipe);
                tokens.add(id == null ? "class:" + com.amicbeam.beyondcraftlines.client.JeiRecipeSourceFingerprint
                        .stableClassName(recipe.getClass()) : "id:" + id);
            }
            catch (RuntimeException | LinkageError exception)
            {
                complete = failed = true;
                LOGGER.warn("{} client JEI source scan failed type={} error={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX, type, exception.toString());
            }
            finally
            {
                RecipeIndexDiagnostics.record("jei_source_scan", System.nanoTime() - started,
                        "<source>", type, category.getClass(), -1, -1);
            }
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
        RecipeIndexDiagnostics.reset();
        resetPersistentCache();
        runtime = null;
        com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupProfileRegistry.clear();
        TYPE_QUEUE.clear();
        TYPE_STATE.clear();
        allTypesRequested = false;
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
        private long recipeOrdinal;
        private int failedRecipes;
        private String diagnosticId;

        @SuppressWarnings("unchecked")
        private SearchTask(IRecipeCategory<?> category, ResourceLocation recipeType)
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
                        try { JeiCatalystIndex.captured(recipeType, recipe, values); }
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
                // A consumed bad recipe must not prevent the remaining recipes in this category from loading.
                complete = recipe == null;
                if (++failedRecipes <= 8)
                    LOGGER.warn("Unable to index JEI recipe type={} ordinal={} category={} error={} action={}",
                            recipeType, recipeOrdinal, category.getClass().getName(), exception.toString(),
                            complete ? "abort_broken_lookup" : "skip_recipe");
                return recipe != null;
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
                            ? holder.id() : category.getRegistryName(recipe);
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
        private ResourceLocation recipeType() { return recipeType; }
    }
}
