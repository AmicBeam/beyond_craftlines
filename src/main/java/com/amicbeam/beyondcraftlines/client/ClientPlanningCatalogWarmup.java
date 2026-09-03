package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry;
import com.amicbeam.beyondcraftlines.common.menu.RecipeIndexVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Session-scoped capture with bounded background cache I/O and main-thread recipe access. */
public final class ClientPlanningCatalogWarmup
{
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final long BACKGROUND_TIME_BUDGET_NANOS = 2_000_000L;
    private static final long METRICS_INTERVAL_NANOS = 5_000_000_000L;
    private static final Handle HANDLE = new Handle();
    private static boolean requested;
    private static Set<String> families = Set.of();
    private static Object recipeSource;
    private static List<String> holderIds = List.of();
    private static List<RecipeHolder<?>> pendingHolders = List.of();
    private static long observedVirtualRevision = -1;
    private static long generation;
    private static ClientPlanningCatalogCache.LoadJob loadJob;
    private static ClientRecipePlanner.CatalogBuilder builder;
    private static long startedNanos;
    private static long nextMetricsNanos;
    private static long maxMainSliceNanos;
    private static long snapshotNanos;
    private static boolean completionLogged;
    private static boolean cachePersisted;

    private ClientPlanningCatalogWarmup() {}

    public static synchronized void request(Collection<String> availableFamilies)
    {
        Set<String> next = Set.copyOf(availableFamilies);
        if (requested && families.equals(next)) return;
        if (!families.equals(next)) invalidateCapture();
        requested = true;
        families = next;
    }

    public static synchronized void tick(long timeBudgetNanos)
    {
        long tickStarted = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (!requested || level == null || timeBudgetNanos < 1 || !JeiCatalystIndex.recipeTypesReady(families)) return;
        long revision = VirtualProvisionerRecipeRegistry.revision();
        if (!active() || recipeSource != level.getRecipeManager() || observedVirtualRevision != revision)
        {
            long scanStarted = System.nanoTime();
            List<RecipeHolder<?>> recipes = planningRecipes(level, families);
            long scanElapsed = System.nanoTime() - scanStarted;
            acquire(level, families, recipes);
            snapshotNanos += scanElapsed;
        }
        advance(level, timeBudgetNanos);
        recordFrameSlice(System.nanoTime() - tickStarted);
    }

    public static synchronized void tick()
    { tick(BACKGROUND_TIME_BUDGET_NANOS); }

    public static synchronized Handle acquire(Level level, Collection<String> availableFamilies,
                                              List<RecipeHolder<?>> holders)
    {
        request(availableFamilies);
        Object source = level.getRecipeManager();
        List<String> ids = holders.stream().map(holder -> holder.id().toString()).toList();
        long revision = VirtualProvisionerRecipeRegistry.revision();
        boolean reusable = builder != null && builder.complete() && holderIds.equals(ids);
        if (!reusable && (loadJob == null && builder == null || !holderIds.equals(ids)
                || builder != null && !builder.complete() && recipeSource != source))
            start(holders, ids);
        recipeSource = source;
        observedVirtualRevision = revision;
        if (reusable) logCompletionIfReady();
        return HANDLE;
    }

    private static void start(List<RecipeHolder<?>> holders, List<String> ids)
    {
        cancelLoad();
        generation++;
        holderIds = List.copyOf(ids);
        pendingHolders = List.copyOf(holders);
        builder = null;
        loadJob = ClientPlanningCatalogCache.loadAsync(holderIds, generation);
        startedNanos = System.nanoTime();
        nextMetricsNanos = startedNanos + METRICS_INTERVAL_NANOS;
        maxMainSliceNanos = 0L;
        snapshotNanos = 0L;
        completionLogged = false;
        cachePersisted = false;
        LOGGER.info("{} client planning catalog preparation started holders={} generation={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                holders.size(), generation);
    }

    private static void advance(Level level, long timeBudgetNanos)
    {
        long sliceStarted = System.nanoTime();
        long deadline = sliceStarted + timeBudgetNanos;
        ClientPlanningCatalogCache.LoadJob loading = loadJob;
        if (loading != null)
        {
            try { loading.advance(level, Math.max(1L, deadline - System.nanoTime())); }
            catch (RuntimeException | LinkageError exception)
            {
                LOGGER.warn("{} client planning cache decode failed; falling back to incremental capture",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX, exception);
                loading.cancel();
            }
            if (loading.complete() && loading.generation() == generation)
            {
                builder = ClientRecipePlanner.restored(loading.catalog(), pendingHolders.size());
                cachePersisted = true;
                LOGGER.info("{} client planning cache restored recipes={} readHeaderMs={} decompressParseMs={} ioWallMs={} decodeMs={} queueDepth={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        loading.completedRecipes(), loading.headerMillis(), loading.parseMillis(), loading.ioMillis(),
                        loading.decodeMillis(), loading.queueDepth());
                loadJob = null;
                pendingHolders = List.of();
            }
            else if (loading.terminalWithoutCatalog())
            {
                LOGGER.info("{} client planning cache unavailable state={} ioMs={}; capturing recipes",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        loading.stateName(), loading.ioMillis());
                loadJob = null;
                builder = ClientRecipePlanner.beginCapture(level, pendingHolders);
            }
        }
        if (loadJob == null && builder != null && !builder.complete() && System.nanoTime() < deadline)
            builder.advance(Math.max(1L, deadline - System.nanoTime()));
        long elapsed = System.nanoTime() - sliceStarted;
        maxMainSliceNanos = Math.max(maxMainSliceNanos, elapsed);
        logMetricsIfDue();
        logCompletionIfReady();
    }

    public static synchronized void invalidate()
    { invalidateCapture(); }

    public static synchronized void clear()
    {
        requested = false;
        families = Set.of();
        invalidateCapture();
        com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.clearResolutionKeyCache();
    }

    public static synchronized void pause()
    {
        requested = false;
        recipeSource = null;
        if (!complete()) invalidateCapture();
        com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.clearResolutionKeyCache();
    }

    private static void invalidateCapture()
    {
        generation++;
        cancelLoad();
        recipeSource = null;
        holderIds = List.of();
        pendingHolders = List.of();
        observedVirtualRevision = -1;
        builder = null;
        startedNanos = 0L;
        completionLogged = false;
        cachePersisted = false;
        snapshotNanos = 0L;
    }

    private static void cancelLoad()
    {
        ClientPlanningCatalogCache.LoadJob loading = loadJob;
        loadJob = null;
        if (loading != null) loading.cancel();
    }

    private static boolean active()
    { return loadJob != null || builder != null; }

    private static boolean complete()
    { return builder != null && builder.complete(); }

    public static synchronized void recordFrameSlice(long elapsedNanos)
    { maxMainSliceNanos = Math.max(maxMainSliceNanos, elapsedNanos); }

    private static void logMetricsIfDue()
    {
        long now = System.nanoTime();
        if (now < nextMetricsNanos || complete()) return;
        nextMetricsNanos = now + METRICS_INTERVAL_NANOS;
        LOGGER.info("{} client planning progress stage={} completed={}/{} queueDepth={} maxMainSliceMs={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                loadJob != null ? "cache_decode" : "recipe_capture", HANDLE.completedRecipes(),
                HANDLE.totalRecipes(), loadJob == null ? 0 : loadJob.queueDepth(), maxMainSliceNanos / 1_000_000L);
        maxMainSliceNanos = 0L;
    }

    private static void logCompletionIfReady()
    {
        if (!complete() || completionLogged) return;
        completionLogged = true;
        if (!cachePersisted)
        {
            Level level = Minecraft.getInstance().level;
            if (level != null) ClientPlanningCatalogCache.save(level, holderIds, builder.catalog());
            cachePersisted = true;
        }
        LOGGER.info("{} client planning catalog ready holders={} elapsedMs={} snapshotMs={} candidateMs={} candidateSteps={} mergeMs={} maxMainSliceMs={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX, holderIds.size(),
                (System.nanoTime() - startedNanos) / 1_000_000L, snapshotNanos / 1_000_000L,
                builder.captureMillis(), builder.captureSteps(), builder.mergeMillis(),
                maxMainSliceNanos / 1_000_000L);
    }

    private static List<RecipeHolder<?>> planningRecipes(Level level, Set<String> availableFamilies)
    {
        // Keep expensive output resolution inside the cooperative capture cursor. Doing it in
        // this snapshot produced a single multi-second render-thread stall on large packs.
        java.util.LinkedHashMap<String, RecipeHolder<?>> selected =
                new java.util.LinkedHashMap<>();
        java.util.stream.Stream.concat(RecipePlanningService.allRecipes(level).stream(),
                VirtualProvisionerRecipeRegistry.recipes().stream()).forEach(holder -> {
            String family = RecipePlanningService.family(holder);
            if (RecipePlanningService.supported(holder)
                    && VanillaProvisionerRecipeTypes.isPotentialNetworkExecutable(family)
                    && RecipeIndexVisibility.includes(family, availableFamilies))
                selected.putIfAbsent(holder.id().toString(), holder);
        });
        return selected.values().stream().sorted(java.util.Comparator.comparing(
                holder -> holder.id().toString())).toList();
    }

    public static final class Handle
    {
        private Handle() {}
        public void advance(long timeBudgetNanos)
        {
            Level level = Minecraft.getInstance().level;
            if (level != null) ClientPlanningCatalogWarmup.advance(level, timeBudgetNanos);
        }
        public boolean complete() { return ClientPlanningCatalogWarmup.complete(); }
        public ClientRecipePlanner.Catalog catalog()
        {
            if (!complete()) throw new IllegalStateException("planning catalog is not ready");
            return builder.catalog();
        }
        public int completedRecipes()
        {
            if (loadJob != null) return loadJob.completedRecipes();
            return builder == null ? 0 : builder.completedRecipes();
        }
        public int totalRecipes()
        {
            if (loadJob != null) return loadJob.totalRecipes();
            return builder == null ? pendingHolders.size() : builder.totalRecipes();
        }
    }
}
