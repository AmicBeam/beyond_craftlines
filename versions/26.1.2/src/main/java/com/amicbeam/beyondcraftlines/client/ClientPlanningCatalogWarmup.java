package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
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

/** Session-scoped, main-thread incremental capture shared by world warmup and order screens. */
public final class ClientPlanningCatalogWarmup
{
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final long TIME_BUDGET_NANOS = 2_000_000L;
    private static boolean requested;
    private static Set<String> families = Set.of();
    private static Object recipeSource;
    private static List<String> holderIds = List.of();
    private static long observedVirtualRevision = -1;
    private static ClientRecipePlanner.CatalogBuilder builder;
    private static long startedNanos;
    private static boolean completionLogged;

    private ClientPlanningCatalogWarmup() {}

    public static synchronized void request(Collection<String> availableFamilies)
    {
        Set<String> next = Set.copyOf(availableFamilies);
        if (requested && families.equals(next)) return;
        requested = true;
        families = next;
        invalidateCapture();
    }

    public static synchronized void tick()
    {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (!requested || level == null || !JeiCatalystIndex.recipeTypesReady(families)) return;
        long revision = VirtualProvisionerRecipeRegistry.revision();
        if (builder == null || recipeSource != level
                || observedVirtualRevision != revision)
            acquire(level, families, planningRecipes(level, families));
        if (builder != null && !builder.complete())
            builder.advance(CraftlinesConfig.RECIPE_INDEX_MAX_PER_TICK.get(), TIME_BUDGET_NANOS);
        logCompletionIfReady();
    }

    public static synchronized ClientRecipePlanner.CatalogBuilder acquire(
            Level level, Collection<String> availableFamilies, List<RecipeHolder<?>> holders)
    {
        request(availableFamilies);
        Object source = level;
        List<String> ids = holders.stream().map(holder -> holder.id().toString()).toList();
        long revision = VirtualProvisionerRecipeRegistry.revision();
        if (builder == null || recipeSource != source || !holderIds.equals(ids))
        {
            recipeSource = source;
            holderIds = ids;
            builder = ClientRecipePlanner.beginCapture(level, holders);
            startedNanos = System.nanoTime();
            completionLogged = false;
            LOGGER.info("{} client planning catalog warmup started holders={} cached={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    holders.size(), builder.completedRecipes());
        }
        observedVirtualRevision = revision;
        logCompletionIfReady();
        return builder;
    }

    public static synchronized void invalidate()
    { invalidateCapture(); }

    public static synchronized void clear()
    {
        requested = false;
        families = Set.of();
        invalidateCapture();
    }

    private static void invalidateCapture()
    {
        recipeSource = null;
        holderIds = List.of();
        observedVirtualRevision = -1;
        builder = null;
        startedNanos = 0L;
        completionLogged = false;
    }

    private static void logCompletionIfReady()
    {
        if (builder == null || !builder.complete() || completionLogged) return;
        completionLogged = true;
        LOGGER.info("{} client planning catalog warmup complete holders={} elapsedMs={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                holderIds.size(), (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static List<RecipeHolder<?>> planningRecipes(Level level, Set<String> availableFamilies)
    {
        return RecipePlanningService.visibleRecipes(level).stream()
                .filter(holder -> VanillaProvisionerRecipeTypes.isPotentialNetworkExecutable(
                        RecipePlanningService.family(holder)))
                .filter(holder -> RecipeIndexVisibility.includes(
                        RecipePlanningService.family(holder), availableFamilies))
                .toList();
    }
}
