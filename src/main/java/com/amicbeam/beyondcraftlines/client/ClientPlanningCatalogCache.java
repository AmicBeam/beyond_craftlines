package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned client cache with streaming I/O and no catalog-size cap. */
final class ClientPlanningCatalogCache
{
    static final int MAGIC = 0x42434C43;
    static final int VERSION = 4;
    // Stable across logins. Same-ID content changes are explicitly refreshed with /craftlines reload.
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), runnable -> {
        Thread thread = new Thread(runnable, "beyond-craftlines-planning-cache");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    private ClientPlanningCatalogCache() {}

    static void invalidateDisk() throws IOException
    { invalidate(path()); }

    static void invalidate(Path cachePath) throws IOException
    {
        PlanningCatalogCacheFiles.invalidate(cachePath);
    }

    static LoadJob loadAsync(List<String> holderIds, long generation)
    { return loadAsync(path(), holderIds, generation); }

    static LoadJob loadAsync(Level level, List<String> holderIds, long generation)
    {
        LoadJob job = new LoadJob(path(), generation, List.copyOf(holderIds), level, null);
        job.start();
        return job;
    }

    static LoadJob loadAsync(Level level, List<String> holderIds, long generation,
                             List<net.minecraft.world.item.crafting.RecipeHolder<?>> holders)
    {
        LoadJob job = new LoadJob(path(), generation, List.copyOf(holderIds), level, List.copyOf(holders));
        job.start();
        return job;
    }

    static LoadJob loadAsync(Path cachePath, List<String> holderIds, long generation)
    {
        LoadJob job = new LoadJob(cachePath, generation, List.copyOf(holderIds), null, null);
        job.start();
        return job;
    }

    static LoadJob loadAsync(Path cachePath, List<String> holderIds, long generation,
                             java.util.Map<ResourceLocation, ResourceLocation> migrations)
    {
        LoadJob job = new LoadJob(cachePath, generation, List.copyOf(holderIds), null, null, java.util.Map.copyOf(migrations));
        job.start();
        return job;
    }

    static void save(Level level, List<String> holderIds, ClientRecipePlanner.Catalog catalog)
    {
        saveAsync(level, path(), holderIds, catalog);
    }

    static Future<?> saveAsync(Level level, Path cachePath, List<String> holderIds,
                               ClientRecipePlanner.Catalog catalog)
    {
        return saveWithRegistryAsync(level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess(),
                cachePath, holderIds, catalog);
    }

    static Future<?> saveWithRegistryAsync(net.minecraft.core.RegistryAccess registryAccess, Path cachePath,
                                          List<String> holderIds, ClientRecipePlanner.Catalog catalog)
    {
        List<String> stableIds = List.copyOf(holderIds);
        long revision = PlanningCatalogCacheFiles.revision(cachePath);
        try
        {
            return IO.submit(() -> write(registryAccess, cachePath, revision, stableIds, catalog));
        }
        catch (RejectedExecutionException exception)
        {
            LOGGER.warn("{} client planning cache save skipped reason=io_queue_full path={} recipes={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    cachePath, catalog.recipes().size());
            return null;
        }
    }

    private static void write(net.minecraft.core.RegistryAccess registryAccess, Path path, long revision, List<String> holderIds,
                              ClientRecipePlanner.Catalog catalog)
    {
        long started = System.nanoTime();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try
        {
            PlanningCatalogCacheCounts budget = new PlanningCatalogCacheCounts();
            budget.addRecipes(catalog.recipes().size());
            Files.createDirectories(path.getParent());
            LOGGER.info("{} client planning cache save started path={} recipes={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    path, catalog.recipes().size());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary)))))
            {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                writeString(output, fingerprint(holderIds), budget);
                output.writeInt(catalog.recipes().size());
                for (ClientRecipePlanner.Recipe recipe : catalog.recipes())
                    writeRecipe(output, registryAccess, recipe, budget);
            }
            long bytes = Files.size(temporary);
            var result = PlanningCatalogCacheFiles.install(path, temporary, revision);
            if (result == PlanningCatalogCacheFiles.InstallResult.INSTALLED)
                LOGGER.info("{} client planning cache saved path={} recipes={} entries={} bytes={} elapsedMs={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        path, catalog.recipes().size(), budget.entries(), bytes,
                        (System.nanoTime() - started) / 1_000_000L);
            else LOGGER.warn("{} client planning cache save skipped reason={} path={} recipes={} bytes={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    result, path, catalog.recipes().size(), bytes);
        }
        catch (IOException | RuntimeException | LinkageError exception)
        {
            LOGGER.warn("{} client planning cache save failed path={} recipes={} elapsedMs={} error={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    path, catalog.recipes().size(), (System.nanoTime() - started) / 1_000_000L,
                    exception.toString());
            try { Files.deleteIfExists(temporary); }
            catch (IOException ignored) {}
        }
    }

    private static void writeRecipe(DataOutputStream output, net.minecraft.core.RegistryAccess registryAccess,
                                    ClientRecipePlanner.Recipe recipe, PlanningCatalogCacheCounts budget) throws IOException
    {
        writeString(output, recipe.id().toString(), budget);
        writeString(output, recipe.family(), budget);
        writeKey(output, registryAccess, recipe.output(), budget);
        output.writeLong(recipe.outputCount());
        writeString(output, recipe.outputMatch().name(), budget);
        budget.addByproducts(recipe.byproducts().size());
        output.writeInt(recipe.byproducts().size());
        for (var byproduct : recipe.byproducts())
        {
            writeKey(output, registryAccess, byproduct.key(), budget);
            output.writeLong(byproduct.amount());
        }
        budget.addSlots(recipe.slots().size());
        output.writeInt(recipe.slots().size());
        for (ClientRecipePlanner.Slot slot : recipe.slots())
        {
            output.writeInt(slot.index());
            writeString(output, slot.use().kind().name(), budget);
            output.writeInt(slot.use().damagePerCraft());
            budget.addCandidates(slot.candidates().size());
            output.writeInt(slot.candidates().size());
            for (ClientRecipePlanner.Candidate candidate : slot.candidates())
            {
                writeKey(output, registryAccess, candidate.key(), budget);
                output.writeLong(candidate.count());
                writeString(output, candidate.explicitSelectionItem() == null
                        ? "" : candidate.explicitSelectionItem().toString(), budget);
                writeString(output, candidate.explicitSelection() == null ? "" : candidate.explicitSelection(), budget);
            }
        }
    }

    private static EncodedRecipe readEncodedRecipe(DataInputStream input, PlanningCatalogCacheCounts budget,
                                                   NbtAccounter nbtBudget) throws IOException
    {
        ResourceLocation id = ResourceLocation.tryParse(readString(input, budget));
        String family = readString(input, budget);
        EncodedKey output = readEncodedKey(input, budget, nbtBudget);
        long outputCount = input.readLong();
        RecipeIoProfileRegistry.OutputMatchSemantics outputMatch =
                RecipeIoProfileRegistry.OutputMatchSemantics.valueOf(readString(input, budget));
        int byproductCount = nonNegative(input.readInt());
        budget.addByproducts(byproductCount);
        List<EncodedYield> byproducts = new ArrayList<>();
        for (int i = 0; i < byproductCount; i++)
            byproducts.add(new EncodedYield(readEncodedKey(input, budget, nbtBudget), input.readLong()));
        int slotCount = nonNegative(input.readInt());
        budget.addSlots(slotCount);
        List<EncodedSlot> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++)
        {
            int slotIndex = input.readInt();
            VirtualInputUse.Kind kind = VirtualInputUse.Kind.valueOf(readString(input, budget));
            VirtualInputUse use = new VirtualInputUse(kind, input.readInt());
            int candidateCount = nonNegative(input.readInt());
            budget.addCandidates(candidateCount);
            List<EncodedCandidate> candidates = new ArrayList<>();
            for (int candidate = 0; candidate < candidateCount; candidate++)
            {
                EncodedKey key = readEncodedKey(input, budget, nbtBudget);
                long count = input.readLong();
                String selectedItem = readString(input, budget);
                ResourceLocation item = selectedItem.isEmpty() ? null : ResourceLocation.tryParse(selectedItem);
                candidates.add(new EncodedCandidate(key, count, item, readString(input, budget)));
            }
            slots.add(new EncodedSlot(slotIndex, List.copyOf(candidates), use));
        }
        if (id == null) throw new IOException("invalid cached recipe");
        return new EncodedRecipe(id, family, output, outputCount, outputMatch, List.copyOf(slots), List.copyOf(byproducts));
    }

    static void writeKey(DataOutputStream output, Level level, IStackKey<?> key, PlanningCatalogCacheCounts budget) throws IOException
    {
        writeKey(output, level.registryAccess(), key, budget);
    }

    static void writeKey(DataOutputStream output, net.minecraft.core.RegistryAccess registryAccess,
                         IStackKey<?> key, PlanningCatalogCacheCounts budget) throws IOException
    {
        writeString(output, key.getTypeId().toString(), budget);
        NbtIo.write(key.serializeNBT(registryAccess), output);
    }

    static EncodedKey readEncodedKey(DataInputStream input, PlanningCatalogCacheCounts budget,
                                             NbtAccounter nbtBudget) throws IOException
    {
        ResourceLocation type = ResourceLocation.tryParse(readString(input, budget));
        CompoundTag encoded = NbtIo.read(input, nbtBudget);
        if (type == null || encoded == null) throw new IOException("invalid cached stack key");
        return new EncodedKey(type, encoded);
    }

    static int nonNegative(int value) throws IOException
    {
        if (value < 0) throw new IOException("negative cache length/count value=" + value);
        return value;
    }

    static void writeString(DataOutputStream output, String value, PlanningCatalogCacheCounts budget) throws IOException
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        budget.addStringBytes(bytes.length);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static String readString(DataInputStream input, PlanningCatalogCacheCounts budget) throws IOException
    {
        int length = nonNegative(input.readInt());
        budget.addStringBytes(length);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new java.io.EOFException("truncated cache string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String fingerprint(List<String> holderIds)
    { return PlanningCatalogCacheIdentity.fingerprint(holderIds); }

    static Path path()
    {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        var remote = minecraft.getCurrentServer();
        String scope = server != null ? "world:" + server.getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()
                : "server:" + (remote != null ? remote.ip : "unknown");
        return cachePath(minecraft.gameDirectory.toPath(),
                net.minecraft.SharedConstants.getCurrentVersion().getName(), scope);
    }

    static Path cachePath(Path gameDirectory, String gameVersion, String scope)
    { return PlanningCatalogCacheIdentity.cachePath(gameDirectory, gameVersion, scope); }

    static final class LoadJob
    {
        private final long generation;
        private final Path cachePath;
        private final List<String> holderIds;
        private final Level backgroundLevel;
        private final List<net.minecraft.world.item.crafting.RecipeHolder<?>> currentHolders;
        private volatile boolean exactMatch = true;
        private volatile ClientRecipePlanner.CatalogBuilder reconciled;
        private volatile com.amicbeam.beyondcraftlines.common.crafting.ClientRecipeLookupIndex.Builder restoredLookup;
        private volatile boolean buildingLookup;
        private volatile int removedRecipeIds;
        private final java.util.Map<ResourceLocation, ResourceLocation> idMigrations;
        private final ArrayBlockingQueue<EncodedRecipe> queue = new ArrayBlockingQueue<>(2);
        private final List<ClientRecipePlanner.Recipe> decoded = new ArrayList<>();
        private final java.util.Map<IStackKey<?>, IStackKey<?>> decodedKeys = new java.util.HashMap<>();
        private volatile State state = State.QUEUED;
        private volatile String reason = "pending";
        private volatile int totalRecipes;
        private volatile int decodedRecipes;
        private volatile Future<?> future;
        private volatile boolean cancelled;
        private volatile long ioNanos;
        private volatile long headerNanos;
        private volatile long parseNanos;
        private volatile long decodeNanos;
        private DecodeCursor decoder;
        private volatile ClientRecipePlanner.Catalog catalog;

        private LoadJob(Path cachePath, long generation, List<String> holderIds, Level backgroundLevel,
                        List<net.minecraft.world.item.crafting.RecipeHolder<?>> currentHolders)
        { this(cachePath, generation, holderIds, backgroundLevel, currentHolders, ClientJeiRecipeCache.idMigrations()); }

        private LoadJob(Path cachePath, long generation, List<String> holderIds, Level backgroundLevel,
                        List<net.minecraft.world.item.crafting.RecipeHolder<?>> currentHolders,
                        java.util.Map<ResourceLocation, ResourceLocation> idMigrations)
        { this.cachePath = cachePath; this.generation = generation; this.holderIds = holderIds;
            this.backgroundLevel = backgroundLevel; this.currentHolders = currentHolders; this.idMigrations = idMigrations; }

        private void start()
        {
            try { future = IO.submit(this::read); }
            catch (RejectedExecutionException exception) { miss("io_queue_full"); }
        }

        private void read()
        {
            long started = System.nanoTime();
            try
            {
                if (!Files.isRegularFile(cachePath)) { miss("file_missing"); return; }
                try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                        new GZIPInputStream(Files.newInputStream(cachePath)))))
                {
                    PlanningCatalogCacheCounts budget = new PlanningCatalogCacheCounts();
                    NbtAccounter nbtBudget = NbtAccounter.unlimitedHeap();
                    if (input.readInt() != MAGIC) { miss("invalid_magic"); return; }
                    if (input.readInt() != VERSION) { miss("format_version_changed"); return; }
                    String savedFingerprint = readString(input, budget);
                    String currentFingerprint = fingerprint(holderIds);
                    if (!currentFingerprint.equals(savedFingerprint))
                    {
                        exactMatch = false;
                        reason = "recipe_ids_changed";
                        LOGGER.info("{} client planning cache fingerprint changed; reconciling by recipe id path={} saved={} current={} holders={}",
                                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                                cachePath, savedFingerprint, currentFingerprint, holderIds.size());
                    }
                    java.util.Set<String> currentIds = !exactMatch
                            ? new java.util.HashSet<>(holderIds) : null;
                    java.util.Set<ResourceLocation> removedIds = new java.util.HashSet<>();
                    java.util.Set<ResourceLocation> migratedRecords = new java.util.HashSet<>();
                    totalRecipes = nonNegative(input.readInt());
                    budget.addRecipes(totalRecipes);
                    headerNanos = System.nanoTime() - started;
                    state = State.READING;
                    for (int i = 0; i < totalRecipes && !cancelled; i++)
                    {
                        long parseStarted = System.nanoTime();
                        EncodedRecipe encoded = readEncodedRecipe(input, budget, nbtBudget);
                        ResourceLocation migratedId = idMigrations.get(encoded.id());
                        if (migratedId != null)
                        {
                            // Canonical virtual IDs define one output. Old aliases can collapse to the same recipe.
                            // Native IDs are never migrated here, so their multiple output directions stay intact.
                            if (!migratedRecords.add(migratedId)) { decodedRecipes = i + 1; continue; }
                            encoded = new EncodedRecipe(migratedId, encoded.family(), encoded.output(),
                                    encoded.outputCount(), encoded.outputMatch(), encoded.slots(), encoded.byproducts());
                        }
                        if (currentIds != null && !currentIds.contains(encoded.id().toString()))
                        {
                            removedIds.add(encoded.id());
                            removedRecipeIds = removedIds.size();
                            decodedRecipes = i + 1;
                            continue;
                        }
                        parseNanos += System.nanoTime() - parseStarted;
                        if (backgroundLevel == null) queue.put(encoded);
                        else
                        {
                            long decodeStarted = System.nanoTime();
                            DecodeCursor cursor = new DecodeCursor(encoded);
                            ClientRecipePlanner.Recipe recipe = null;
                            while (recipe == null && !cancelled) recipe = cursor.advance(backgroundLevel.registryAccess());
                            if (recipe != null)
                            {
                                decoded.add(recipe);
                                decodedRecipes = i + 1;
                            }
                            decodeNanos += System.nanoTime() - decodeStarted;
                        }
                    }
                    if (!cancelled && input.read() != -1) throw new IOException("trailing planning cache data");
                    if (cancelled) state = State.CANCELLED;
                    else if (backgroundLevel != null)
                    {
                        long decodeStarted = System.nanoTime();
                        ClientRecipePlanner.Catalog loaded = new ClientRecipePlanner.Catalog(decoded);
                        if (!exactMatch && currentHolders != null)
                            reconciled = ClientRecipePlanner.beginCapture(backgroundLevel, currentHolders, loaded);
                        ClientRecipePlanner.Catalog readyCatalog = exactMatch ? loaded
                                : reconciled != null && reconciled.complete() ? reconciled.catalog() : null;
                        if (readyCatalog != null)
                        {
                            restoredLookup = com.amicbeam.beyondcraftlines.common.crafting.ClientRecipeLookupIndex.begin(readyCatalog);
                            buildingLookup = true;
                            while (!restoredLookup.complete() && !cancelled) restoredLookup.advance(10_000_000L);
                            buildingLookup = false;
                        }
                        if (cancelled) { state = State.CANCELLED; return; }
                        catalog = loaded;
                        decodedKeys.clear();
                        decodeNanos += System.nanoTime() - decodeStarted;
                        state = State.EOF;
                    }
                    else state = State.EOF;
                }
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                state = State.CANCELLED;
            }
            catch (IOException | RuntimeException | LinkageError exception)
            {
                reason = exception.toString();
                state = State.FAILED;
                LOGGER.warn("{} client planning cache read failed path={} error={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        cachePath, exception.toString());
            }
            finally { ioNanos = System.nanoTime() - started; }
        }

        synchronized void advance(Level level, long timeBudgetNanos)
        {
            if (backgroundLevel != null) return;
            if (catalog != null || terminalWithoutCatalog() || timeBudgetNanos < 1) return;
            long started = System.nanoTime();
            int processed = 0;
            while (processed < 1 || System.nanoTime() - started < timeBudgetNanos)
            {
                if (decoder == null)
                {
                    EncodedRecipe encoded = queue.poll();
                    if (encoded == null) break;
                    decoder = new DecodeCursor(encoded);
                }
                ClientRecipePlanner.Recipe recipe = decoder.advance(level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess());
                if (recipe != null)
                {
                    decoded.add(recipe);
                    decodedRecipes = decoded.size();
                    decoder = null;
                }
                processed++;
            }
            decodeNanos += System.nanoTime() - started;
            // Header counts include removed IDs and legacy aliases collapsed during migration.
            if (state == State.EOF && queue.isEmpty() && decoder == null)
            {
                catalog = new ClientRecipePlanner.Catalog(decoded);
                decodedKeys.clear();
            }
        }

        private IStackKey<?> decodeKey(EncodedKey encoded, net.minecraft.core.RegistryAccess registryAccess)
        {
            IStackKey<?> key = StackKeyRegistry.getType(encoded.type())
                    .deserializeNBT(encoded.nbt(), registryAccess);
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("invalid cached stack key");
            IStackKey<?> existing = decodedKeys.putIfAbsent(key, key);
            return existing == null ? key : existing;
        }

        synchronized void cancel()
        {
            cancelled = true;
            state = State.CANCELLED;
            queue.clear();
            if (backgroundLevel == null)
            {
                decoded.clear();
                decodedKeys.clear();
                decoder = null;
            }
            Future<?> task = future;
            if (task != null) task.cancel(true);
        }

        long generation() { return generation; }
        synchronized boolean complete() { return catalog != null; }
        synchronized ClientRecipePlanner.Catalog catalog()
        {
            if (catalog == null) throw new IllegalStateException("cache is not decoded");
            return catalog;
        }
        boolean terminalWithoutCatalog()
        { return state == State.MISS || state == State.FAILED || state == State.CANCELLED; }
        int completedRecipes() { return decodedRecipes; }
        int totalRecipes() { return totalRecipes > 0 ? totalRecipes : holderIds.size(); }
        int queueDepth() { return queue.size(); }
        long ioMillis() { return ioNanos / 1_000_000L; }
        long headerMillis() { return headerNanos / 1_000_000L; }
        long parseMillis() { return parseNanos / 1_000_000L; }
        long decodeMillis() { return decodeNanos / 1_000_000L; }
        private void miss(String reason) { this.reason = reason; state = State.MISS; }
        boolean exactMatch() { return exactMatch; }
        int removedRecipeIds() { return removedRecipeIds; }
        boolean buildingLookup() { return buildingLookup; }
        com.amicbeam.beyondcraftlines.common.crafting.ClientRecipeLookupIndex.Builder restoredLookup()
        { return restoredLookup; }
        ClientRecipePlanner.CatalogBuilder reconciled() { return reconciled; }
        String reason() { return reason; }
        Path cachePath() { return cachePath; }
        String stateName() { return state.name().toLowerCase(java.util.Locale.ROOT); }

        private final class DecodeCursor
        {
            private final EncodedRecipe encoded;
            private final List<ClientRecipePlanner.Slot> slots;
            private IStackKey<?> output;
            private int byproductIndex;
            private final List<com.wintercogs.beyonddimensions.api.storage.key.KeyAmount> byproducts = new ArrayList<>();
            private int slotIndex;
            private int candidateIndex;
            private List<ClientRecipePlanner.Candidate> candidates;

            private DecodeCursor(EncodedRecipe encoded)
            { this.encoded = encoded; this.slots = new ArrayList<>(encoded.slots().size()); }

            private ClientRecipePlanner.Recipe advance(net.minecraft.core.RegistryAccess registryAccess)
            {
                if (output == null)
                {
                    output = decodeKey(encoded.output(), registryAccess);
                    return null;
                }
                if (byproductIndex < encoded.byproducts().size())
                {
                    EncodedYield byproduct = encoded.byproducts().get(byproductIndex++);
                    byproducts.add(new com.wintercogs.beyonddimensions.api.storage.key.KeyAmount(
                            decodeKey(byproduct.key(), registryAccess), byproduct.amount()));
                    return null;
                }
                if (slotIndex < encoded.slots().size())
                {
                    EncodedSlot slot = encoded.slots().get(slotIndex);
                    if (candidates == null) candidates = new ArrayList<>(slot.candidates().size());
                    if (candidateIndex < slot.candidates().size())
                    {
                        EncodedCandidate candidate = slot.candidates().get(candidateIndex++);
                        IStackKey<?> key = decodeKey(candidate.key(), registryAccess);
                        candidates.add(candidate.selection().isEmpty()
                                ? new ClientRecipePlanner.Candidate(key, candidate.count())
                                : new ClientRecipePlanner.Candidate(key, candidate.count(),
                                        candidate.selectionItem(), candidate.selection()));
                        return null;
                    }
                    slots.add(new ClientRecipePlanner.Slot(slot.index(), candidates, slot.use()));
                    slotIndex++;
                    candidateIndex = 0;
                    candidates = null;
                    return null;
                }
                return new ClientRecipePlanner.Recipe(encoded.id(), encoded.family(), output,
                        encoded.outputCount(), encoded.outputMatch(), slots, byproducts);
            }
        }
    }

    private enum State { QUEUED, READING, EOF, MISS, FAILED, CANCELLED }
    record EncodedKey(ResourceLocation type, CompoundTag nbt) {}
    private record EncodedCandidate(EncodedKey key, long count, ResourceLocation selectionItem, String selection) {}
    private record EncodedYield(EncodedKey key, long amount) {}
    private record EncodedSlot(int index, List<EncodedCandidate> candidates, VirtualInputUse use) {}
    private record EncodedRecipe(ResourceLocation id, String family, EncodedKey output, long outputCount,
                                 RecipeIoProfileRegistry.OutputMatchSemantics outputMatch,
                                 List<EncodedSlot> slots, List<EncodedYield> byproducts) {}

}
