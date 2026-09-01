package com.amicbeam.beyondcraftlines.common.menu;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import java.util.Collection;
import java.util.Iterator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.Gson;

public final class CraftlineOrderMenu extends AbstractContainerMenu
{
    private static final Map<Object, RecipeIndex> RECIPE_INDEX_CACHE = new WeakHashMap<>();
    private static final Map<Object, RecipeIndex> CLIENT_RECIPE_INDEX_CACHE = new WeakHashMap<>();
    private static final Set<MinecraftServer> FORCED_SERVER_INDEX_REBUILDS =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Gson INDEX_GSON = new Gson();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final ExecutorService INDEX_IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "beyond-craftlines-recipe-index-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Player player;
    private final int networkId;
    private final IStackKey<?> initialTarget;
    private final ResourceLocation initialRecipe;
    private final boolean initialRecipePinned;
    private final Set<String> availableFamilies;
    private final RecipeIndex recipeIndex;
    private final RecipeHolder<?> initialRecipeHolder;
    private final BlockPos dashboardPosition;
    private final boolean initialBlockingMode;
    private final long initialDashboardDesired;
    private final String initialDashboardStockMode;
    private String initialError;
    private final SimpleContainerData serverIndexProgress = new SimpleContainerData(2);

    public CraftlineOrderMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, data.readVarInt(), IStackKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) data),
                optionalId(data.readUtf()), data.readBoolean(), readFamilies(data),
                data.readBoolean() ? data.readBlockPos() : null, data.readBoolean(),
                data.readVarLong(), data.readUtf(16));
        this.initialError = data.readableBytes() > 0 ? data.readUtf(512) : "";
    }

    public CraftlineOrderMenu(int id, Inventory inventory, int networkId, IStackKey<?> initialTarget,
                              ResourceLocation initialRecipe, boolean initialRecipePinned,
                              Set<String> availableFamilies)
    { this(id, inventory, networkId, initialTarget, initialRecipe, initialRecipePinned,
            availableFamilies, null, false, 1, "network"); }

    public CraftlineOrderMenu(int id, Inventory inventory, int networkId, IStackKey<?> initialTarget,
                              ResourceLocation initialRecipe, boolean initialRecipePinned,
                              Set<String> availableFamilies, BlockPos dashboardPosition,
                              boolean initialBlockingMode, long initialDashboardDesired,
                              String initialDashboardStockMode)
    {
        super(CraftlinesMenus.ORDER.get(), id);
        this.player = inventory.player;
        this.networkId = networkId;
        this.initialTarget = initialTarget;
        this.initialRecipe = initialRecipe;
        this.initialRecipePinned = initialRecipePinned;
        this.availableFamilies = Set.copyOf(availableFamilies);
        this.dashboardPosition = dashboardPosition == null ? null : dashboardPosition.immutable();
        this.initialBlockingMode = initialBlockingMode;
        this.initialDashboardDesired = Math.max(1, initialDashboardDesired);
        this.initialDashboardStockMode = initialDashboardStockMode == null ? "network" : initialDashboardStockMode;
        this.initialError = "";
        var level = player.level();
        // The server-side menu only synchronizes state; recipe lookup and incremental indexing are
        // client-owned. Enumerating every display recipe here delayed the menu-open packet.
        this.recipeIndex = level.isClientSide() ? clientIndex(level) : new RecipeIndex(List.of(), level);
        this.initialRecipeHolder = initialRecipe == null ? null
                : findDisplayRecipe(level, initialRecipe);
        addDataSlots(serverIndexProgress);
        updateServerIndexProgress();
    }

    public int networkId() { return networkId; }
    public IStackKey<?> initialTarget() { return initialTarget; }
    public String targetToken()
    { return com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(initialTarget); }
    public ResourceLocation initialRecipe() { return initialRecipe; }
    public boolean initialRecipePinned() { return initialRecipePinned; }
    public RecipeHolder<?> initialRecipeHolder() { return initialRecipeHolder; }
    public Set<String> availableFamilies() { return availableFamilies; }
    public boolean dashboardConfiguration() { return dashboardPosition != null; }
    public BlockPos dashboardPosition() { return dashboardPosition; }
    public boolean initialBlockingMode() { return initialBlockingMode; }
    public long initialDashboardDesired() { return initialDashboardDesired; }
    public String initialDashboardStockMode() { return initialDashboardStockMode; }
    public String initialError() { return initialError; }
    public List<RecipeHolder<?>> recipes()
    { return available(displayRecipes(player.level())); }
    public RecipeHolder<?> recipeForOutput(ResourceLocation output)
    { return recipesForOutput(output).stream().findFirst().orElse(null); }
    public List<RecipeHolder<?>> recipesForOutput(ResourceLocation output)
    {
        List<RecipeHolder<?>> virtual = recipes().stream()
                .filter(holder -> RecipeOutputResolver.outputs(holder.value(), player.level().registryAccess())
                        .stream().anyMatch(value -> value.key() instanceof ItemStackKey item
                                && net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(item.getSource()).equals(output))).toList();
        return virtual;
    }
    public List<RecipeHolder<?>> recipesForResourceOutput(IStackKey<?> output)
    {
        List<RecipeHolder<?>> virtual = recipes().stream()
                .filter(holder -> RecipeOutputResolver.outputs(holder.value(), player.level().registryAccess())
                        .stream().anyMatch(value -> com.amicbeam.beyondcraftlines.common.crafting
                                .StackKeyMatch.exact(output, value.key()))).toList();
        return virtual;
    }
    public RecipeHolder<?> recipeForResourceOutput(IStackKey<?> output)
    { return recipesForResourceOutput(output).stream().findFirst().orElse(null); }
    public boolean canPlanTarget(IStackKey<?> output)
    {
        return recipeForResourceOutput(output) != null || initialRecipePinned && initialRecipe != null
                && initialRecipeHolder != null && recipeProduces(initialRecipe, targetToken());
    }
    public RecipeHolder<?> recipe(ResourceLocation id)
    {
        RecipeHolder<?> holder = findDisplayRecipe(player.level(), id);
        return holder != null && available(holder) ? holder : null;
    }
    public ResourceLocation itemOutputForToken(String token)
    {
        return recipes().stream()
                .flatMap(holder -> RecipeOutputResolver.outputs(holder.value(), player.level().registryAccess()).stream())
                .filter(value -> token.equals(com.amicbeam.beyondcraftlines.common.crafting
                        .RecipeResourceResolver.resolutionKey(value.key())) || token.equals(
                        com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(value.key())))
                .filter(value -> value.key() instanceof ItemStackKey).map(value ->
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(((ItemStackKey) value.key()).getSource())).findFirst().orElse(null);
    }
    public boolean recipeProduces(ResourceLocation recipe, String token)
    {
        RecipeHolder<?> holder = findDisplayRecipe(player.level(), recipe);
        return holder != null && RecipeOutputResolver.outputs(holder.value(), player.level().registryAccess())
                .stream().anyMatch(value -> token.equals(com.amicbeam.beyondcraftlines.common.crafting
                        .RecipeResourceResolver.sortKey(value.key())));
    }

    private List<RecipeHolder<?>> available(List<RecipeHolder<?>> holders)
    { return holders.stream().filter(this::available).toList(); }

    private static List<RecipeHolder<?>> mergeRecipes(List<RecipeHolder<?>> first, List<RecipeHolder<?>> second)
    {
        LinkedHashMap<ResourceLocation, RecipeHolder<?>> merged = new LinkedHashMap<>();
        first.forEach(holder -> merged.put(holder.id(), holder));
        second.forEach(holder -> merged.putIfAbsent(holder.id(), holder));
        return merged.values().stream().sorted(java.util.Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    private boolean available(RecipeHolder<?> holder)
    { return RecipeIndexVisibility.includes(RecipePlanningService.family(holder), availableFamilies); }

    public void advanceRecipeIndex(int recipeBudget, long timeBudgetNanos)
    { recipeIndex.advance(recipeBudget, timeBudgetNanos); }
    public boolean recipeIndexComplete() { return recipeIndex.complete(); }
    public int indexedRecipeCandidates() { return recipeIndex.completedCandidates(); }
    public int totalRecipeCandidates() { return recipeIndex.totalCandidates(); }
    public boolean serverRecipeIndexComplete()
    { return true; }
    public int indexedServerRecipeCandidates() { return serverIndexProgress.get(0); }
    public int totalServerRecipeCandidates() { return serverIndexProgress.get(1); }

    @Override public void broadcastChanges()
    {
        updateServerIndexProgress();
        super.broadcastChanges();
    }

    private void ensureRecipeIndexForServer()
    { /* The global server index is built incrementally from the server tick; never block an order request. */ }

    private void updateServerIndexProgress()
    {
        if (!player.level().isClientSide())
        {
            int total = Math.max(1, recipeIndex.totalCandidates());
            serverIndexProgress.set(0, recipeIndex.complete() ? total : recipeIndex.completedCandidates());
            serverIndexProgress.set(1, total);
        }
    }

    public static void tickServerRecipeIndex(MinecraftServer server)
    {}

    public static long serverRecipeEpoch(ServerLevel level, Set<String> availableFamilies)
    { return 0L; }

    private static RecipeIndex serverIndex(ServerLevel level)
    {
        synchronized (RECIPE_INDEX_CACHE)
        {
            return RECIPE_INDEX_CACHE.computeIfAbsent(level.getRecipeManager(), ignored -> {
                Collection<RecipeHolder<?>> recipes = level.getRecipeManager().getRecipes();
                return new RecipeIndex(recipes, recipes.size(), level, indexPath(level.getServer()),
                        FORCED_SERVER_INDEX_REBUILDS.remove(level.getServer()));
            });
        }
    }

    private static RecipeIndex clientIndex(net.minecraft.world.level.Level level)
    {
        Object source = level.getRecipeManager();
        synchronized (CLIENT_RECIPE_INDEX_CACHE)
        {
            return CLIENT_RECIPE_INDEX_CACHE.computeIfAbsent(source,
                    ignored -> new RecipeIndex(baseClientRecipes(level), level));
        }
    }

    /** Stable native base; JEI-only recipes are already keyed and cached separately by the planning catalog. */
    private static List<RecipeHolder<?>> baseClientRecipes(net.minecraft.world.level.Level level)
    {
        return RecipePlanningService.visibleRecipes(level).stream()
                .filter(holder -> com.amicbeam.beyondcraftlines.common.crafting
                        .VanillaProvisionerRecipeTypes.isPotentialNetworkExecutable(
                                RecipePlanningService.family(holder))).toList();
    }

    private static List<RecipeHolder<?>> displayRecipes(net.minecraft.world.level.Level level)
    {
        return mergeRecipes(baseClientRecipes(level), com.amicbeam.beyondcraftlines.common.crafting
                .VirtualProvisionerRecipeRegistry.recipes());
    }

    private static RecipeHolder<?> findDisplayRecipe(net.minecraft.world.level.Level level, ResourceLocation id)
    {
        return level.getRecipeManager().byKey(id).filter(holder ->
                        com.amicbeam.beyondcraftlines.common.crafting.VanillaProvisionerRecipeTypes
                                .isPotentialNetworkExecutable(RecipePlanningService.family(holder)))
                .or(() -> com.amicbeam.beyondcraftlines.common.crafting
                        .VirtualProvisionerRecipeRegistry.find(id)).orElse(null);
    }

    private static RecipeHolder<?> findRecipe(net.minecraft.world.level.Level level, ResourceLocation id)
    { return findDisplayRecipe(level, id); }

    private static Path indexPath(MinecraftServer server)
    {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("beyond_craftlines_recipe_index_v1.json");
    }

    public static void invalidatePersistedServerIndex(MinecraftServer server)
    {
        try { Files.deleteIfExists(indexPath(server)); }
        catch (java.io.IOException ignored) {}
    }

    public static void clearRecipeIndexCache()
    {
        synchronized (RECIPE_INDEX_CACHE) { RECIPE_INDEX_CACHE.clear(); }
        synchronized (CLIENT_RECIPE_INDEX_CACHE) { CLIENT_RECIPE_INDEX_CACHE.clear(); }
    }

    public static void rebuildServerRecipeIndex(MinecraftServer server)
    {
        invalidatePersistedServerIndex(server);
        synchronized (RECIPE_INDEX_CACHE)
        {
            FORCED_SERVER_INDEX_REBUILDS.add(server);
            RecipePlanningService.clearRecipeCache();
        }
        tickServerRecipeIndex(server);
    }

    private static final class RecipeIndex
    {
        private final Iterator<RecipeHolder<?>> candidates;
        private final int totalCandidates;
        private final net.minecraft.world.level.Level level;
        private final List<RecipeHolder<?>> recipes = new ArrayList<>();
        private final Map<ResourceLocation, RecipeHolder<?>> recipesById = new LinkedHashMap<>();
        private final Map<String, ResourceLocation> itemOutputsByToken = new LinkedHashMap<>();
        private final Map<ResourceLocation, Set<String>> outputTokensByRecipe = new LinkedHashMap<>();
        private final Map<ResourceLocation, RecipeHolder<?>> recipeByOutput = new LinkedHashMap<>();
        private final Map<ResourceLocation, List<RecipeHolder<?>>> recipesByOutput = new LinkedHashMap<>();
        private final Map<String, List<RecipeHolder<?>>> recipesByResourceOutput = new LinkedHashMap<>();
        private final Map<String, List<String>> recipeIdsByResourceOutput = new LinkedHashMap<>();
        private final com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator epochAccumulator =
                new com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator();
        private int next;
        private final Path cachePath;
        private final CompletableFuture<PersistedIndex> cacheLoad;
        private boolean cacheChecked;
        private boolean cachePersisted;
        private boolean buildAnnounced;

        private RecipeIndex(List<RecipeHolder<?>> candidates, net.minecraft.world.level.Level level)
        { this(candidates, candidates.size(), level, null, false); }

        private RecipeIndex(Iterable<RecipeHolder<?>> candidates, int totalCandidates,
                            net.minecraft.world.level.Level level, Path cachePath, boolean skipCacheLoad)
        {
            this.candidates = candidates.iterator();
            this.totalCandidates = totalCandidates;
            this.level = level;
            this.cachePath = cachePath;
            this.cacheLoad = cachePath == null || skipCacheLoad ? null : CompletableFuture.supplyAsync(
                    () -> readPersistedIndex(cachePath), INDEX_IO);
        }

        private synchronized void advance(int recipeBudget, long timeBudgetNanos)
        {
            if (recipeBudget < 1 || timeBudgetNanos < 1 || complete()) return;
            if (!loadCacheIfReady()) return;
            announceBuildStarted();
            int end = (int) Math.min(totalCandidates, (long) next + recipeBudget);
            int minimum = Math.min(1, recipeBudget);
            int processed = 0;
            long started = System.nanoTime();
            while (next < end && (processed < minimum || System.nanoTime() - started < timeBudgetNanos))
            {
                if (!candidates.hasNext()) { next = totalCandidates; break; }
                RecipeHolder<?> holder = candidates.next();
                next++;
                if (RecipePlanningService.supported(holder)) add(holder);
                processed++;
            }
            if (complete()) persistAsync();
        }

        private void add(RecipeHolder<?> holder)
        {
            var outputs = RecipeOutputResolver.outputs(holder.value(), level.registryAccess());
            if (outputs.isEmpty()) return;
            String family = RecipePlanningService.family(holder);
            long identity = identityHash(holder, outputs);
            epochAccumulator.add(family, identity);
            recipes.add(holder);
            recipesById.putIfAbsent(holder.id(), holder);
            for (var output : outputs)
            {
                String token = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                        .sortKey(output.key());
                outputTokensByRecipe.computeIfAbsent(holder.id(), ignored -> new LinkedHashSet<>()).add(token);
                recipesByResourceOutput.computeIfAbsent(token, ignored -> new ArrayList<>()).add(holder);
                recipeIdsByResourceOutput.computeIfAbsent(token, ignored -> new ArrayList<>())
                        .add(holder.id().toString());
                if (output.key() instanceof ItemStackKey itemKey)
                {
                    ResourceLocation outputId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(itemKey.getSource());
                    itemOutputsByToken.putIfAbsent(token, outputId);
                    recipeByOutput.putIfAbsent(outputId, holder);
                    recipesByOutput.computeIfAbsent(outputId, ignored -> new ArrayList<>()).add(holder);
                }
            }
        }

        private synchronized List<RecipeHolder<?>> recipes() { return List.copyOf(recipes); }
        private synchronized List<RecipeHolder<?>> recipesForOutput(ResourceLocation output)
        { return List.copyOf(recipesByOutput.getOrDefault(output, List.of())); }
        private synchronized List<RecipeHolder<?>> recipesForResourceOutput(IStackKey<?> output)
        {
            String token = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(output);
            if (!(level instanceof ServerLevel))
                return List.copyOf(recipesByResourceOutput.getOrDefault(token, List.of()));
            List<RecipeHolder<?>> result = new ArrayList<>();
            for (String recipeId : recipeIdsByResourceOutput.getOrDefault(token, List.of()))
            {
                ResourceLocation id = ResourceLocation.tryParse(recipeId);
                RecipeHolder<?> holder = id == null ? null : findRecipe(level, id);
                if (holder == null || !RecipePlanningService.supported(holder)) continue;
                boolean produces = RecipeOutputResolver.outputs(holder.value(), level.registryAccess()).stream()
                        .anyMatch(value -> token.equals(com.amicbeam.beyondcraftlines.common.crafting
                                .RecipeResourceResolver.sortKey(value.key())));
                if (produces) result.add(holder);
            }
            return List.copyOf(result);
        }
        private synchronized RecipeHolder<?> recipe(ResourceLocation id)
        { return level instanceof ServerLevel ? findRecipe(level, id) : recipesById.get(id); }
        private synchronized ResourceLocation itemOutputForToken(String token) { return itemOutputsByToken.get(token); }
        private synchronized boolean recipeProduces(ResourceLocation recipe, String token)
        {
            if (level instanceof ServerLevel)
            {
                RecipeHolder<?> holder = findRecipe(level, recipe);
                return holder != null && RecipeOutputResolver.outputs(holder.value(), level.registryAccess()).stream()
                        .anyMatch(value -> token.equals(com.amicbeam.beyondcraftlines.common.crafting
                                .RecipeResourceResolver.sortKey(value.key())));
            }
            return outputTokensByRecipe.getOrDefault(recipe, Set.of()).contains(token);
        }
        private synchronized int completedCandidates() { return next; }
        private int totalCandidates() { return totalCandidates; }
        private synchronized boolean complete() { return next >= totalCandidates; }

        private boolean loadCacheIfReady()
        {
            if (cacheChecked || cacheLoad == null) return true;
            if (!cacheLoad.isDone()) return false;
            cacheChecked = true;
            PersistedIndex persisted;
            try { persisted = cacheLoad.join(); }
            catch (RuntimeException ignored) { return true; }
            if (persisted == null || persisted.format() != 1 || persisted.totalRecipes() != totalCandidates
                    || persisted.recipesByOutput() == null || persisted.outputsByRecipe() == null)
                return true;
            recipeIdsByResourceOutput.clear();
            persisted.recipesByOutput().forEach((token, ids) ->
                    recipeIdsByResourceOutput.put(token, new ArrayList<>(ids)));
            outputTokensByRecipe.clear();
            persisted.outputsByRecipe().forEach((recipe, tokens) -> {
                ResourceLocation id = ResourceLocation.tryParse(recipe);
                if (id != null) outputTokensByRecipe.put(id, new LinkedHashSet<>(tokens));
            });
            epochAccumulator.restore(persisted.epoch());
            next = totalCandidates;
            cachePersisted = true;
            LOGGER.info("Loaded persisted Craftlines server recipe index with {} recipes", totalCandidates);
            return false;
        }

        private void persistAsync()
        {
            if (cachePath == null || cachePersisted) return;
            cachePersisted = true;
            LinkedHashMap<String, List<String>> byOutput = new LinkedHashMap<>();
            recipeIdsByResourceOutput.forEach((token, ids) -> byOutput.put(token, List.copyOf(ids)));
            LinkedHashMap<String, List<String>> byRecipe = new LinkedHashMap<>();
            outputTokensByRecipe.forEach((recipe, tokens) ->
                    byRecipe.put(recipe.toString(), List.copyOf(tokens)));
            PersistedIndex persisted = new PersistedIndex(1, totalCandidates,
                    Map.copyOf(byOutput), Map.copyOf(byRecipe), epochAccumulator.snapshot());
            MinecraftServer server = ((ServerLevel) level).getServer();
            CompletableFuture.supplyAsync(() -> writePersistedIndex(cachePath, persisted), INDEX_IO)
                    .thenAccept(success -> server.execute(() -> broadcastToPlayers(server,
                            success ? "message.beyond_craftlines.server_recipe_index_complete"
                                    : "error.beyond_craftlines.server_recipe_index_persist_failed",
                            persisted.totalRecipes())));
        }

        private void announceBuildStarted()
        {
            if (buildAnnounced || cachePath == null || !(level instanceof ServerLevel serverLevel)) return;
            buildAnnounced = true;
            broadcastToPlayers(serverLevel.getServer(),
                    "message.beyond_craftlines.server_recipe_index_started", totalCandidates);
        }

        private synchronized long epoch(Set<String> availableFamilies)
        { return epochAccumulator.epoch(availableFamilies); }

        private long identityHash(RecipeHolder<?> holder,
                                  java.util.List<com.wintercogs.beyonddimensions.api.storage.key.KeyAmount> outputs)
        {
            long hash = com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator
                    .mix(0xCBF29CE484222325L, holder.id().toString());
            hash = com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator
                    .mix(hash, RecipePlanningService.family(holder));
            var serializer = holder.value().getSerializer();
            hash = com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator.mix(hash,
                    java.util.Objects.toString(
                    net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer), ""));
            for (var output : outputs.stream().sorted(java.util.Comparator.comparing(value ->
                    com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(value.key())))
                    .toList())
                hash = com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator.mix(hash,
                        com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                        .sortKey(output.key()) + "@" + output.amount());
            for (var ingredient : com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                    .ingredients(holder.value()))
                for (var candidate : ingredient.candidates().stream().sorted(java.util.Comparator.comparing(value ->
                        com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(value.key())))
                        .toList())
                    hash = com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator.mix(hash,
                            ingredient.slot() + "="
                            + com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                            .sortKey(candidate.key()) + "@" + candidate.amount());
            return hash;
        }
    }

    private static PersistedIndex readPersistedIndex(Path path)
    {
        if (!Files.isRegularFile(path)) return null;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        { return INDEX_GSON.fromJson(reader, PersistedIndex.class); }
        catch (Exception exception)
        {
            LOGGER.warn("Unable to read persisted Craftlines recipe index from {}", path, exception);
            return null;
        }
    }

    private static boolean writePersistedIndex(Path path, PersistedIndex persisted)
    {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
            { INDEX_GSON.toJson(persisted, writer); }
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored)
            { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            LOGGER.info("Persisted Craftlines server recipe index with {} recipes", persisted.totalRecipes());
            return true;
        }
        catch (Exception exception)
        {
            LOGGER.warn("Unable to persist Craftlines recipe index to {}", path, exception);
            return false;
        }
    }

    private static void broadcastToPlayers(MinecraftServer server, String translationKey, int recipeCount)
    {
        var message = net.minecraft.network.chat.Component.translatable(translationKey, recipeCount);
        for (var player : server.getPlayerList().getPlayers())
            player.displayClientMessage(message, false);
    }

    private record PersistedIndex(int format, int totalRecipes,
                                  Map<String, List<String>> recipesByOutput,
                                  Map<String, List<String>> outputsByRecipe,
                                  com.amicbeam.beyondcraftlines.common.crafting.RecipeEpochAccumulator.Snapshot epoch) {}

    public boolean canAccessNetwork(Player player)
    {
        if (player != this.player) return false;
        if (player.level().isClientSide()) return true;
        if (networkId < 0) return true;
        DimensionsNet network = DimensionsNet.getNetFromId(networkId);
        return network != null && (network.isOwner(player) || network.isManager(player)
                || network.getPlayers().contains(player.getUUID()));
    }

    private static Set<String> readFamilies(FriendlyByteBuf data)
    {
        int count = Math.min(512, Math.max(0, data.readVarInt()));
        LinkedHashSet<String> families = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) families.add(data.readUtf(256));
        return Set.copyOf(families);
    }

    private static ResourceLocation optionalId(String value)
    { return value == null || value.isBlank() ? null : ResourceLocation.tryParse(value); }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player)
    {
        return canAccessNetwork(player) && (dashboardPosition == null
                || player.blockPosition().distSqr(dashboardPosition) <= 64
                && player.level().getBlockEntity(dashboardPosition)
                instanceof com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity);
    }
}
