package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse;
import com.amicbeam.beyondcraftlines.common.crafting.VirtualProvisionerRecipeRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Persistent JEI execution descriptions; drawable objects never enter this file. */
public final class ClientJeiRecipeCache
{
    static final int MAGIC = 0x42434C4A;
    static final int VERSION = 1;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final String PREFIX = com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX;
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), runnable -> {
        Thread thread = new Thread(runnable, "beyond-craftlines-jei-cache");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private static LoadJob load;
    private static boolean applied;
    private static long nextProgress;

    private ClientJeiRecipeCache() {}

    public record Restored(Set<String> types, Map<String, Set<String>> groups, int recipes) {}

    /** Null means the I/O worker is still reading; an empty result means a cold cache. */
    public static synchronized Restored prepare(Map<String, List<String>> categorySources)
    {
        if (load == null)
        {
            Level level = Minecraft.getInstance().level;
            if (level == null) return null;
            load = loadAsync(path(), level.registryAccess(), categorySources);
            nextProgress = 0L;
        }
        if (!load.finished)
        {
            long now = System.nanoTime();
            if (now >= nextProgress)
            {
                LOGGER.info("{} client JEI cache progress stage=read recipes={}/{}", PREFIX,
                        load.processedRecipes, load.totalRecipes);
                nextProgress = now + 5_000_000_000L;
            }
            return null;
        }
        if (applied) return load.result;
        applied = true;
        if (load.failed) VirtualProvisionerRecipeRegistry.clear();
        return load.result;
    }

    public static synchronized int completedRecipes() { return load == null ? 0 : load.processedRecipes; }
    public static synchronized int totalRecipes() { return load == null ? 0 : load.totalRecipes; }
    public static synchronized boolean loading() { return load != null && !load.finished; }

    public static synchronized void reset()
    {
        if (load != null) load.cancel();
        load = null;
        applied = false;
        VirtualProvisionerRecipeRegistry.cancelClientRestore();
    }

    public static synchronized void invalidateDisk() throws IOException
    {
        PlanningCatalogCacheFiles.invalidate(path());
        reset();
    }

    public static synchronized void save(Set<String> completedTypes, Map<String, Set<String>> groups)
    {
        if (load == null || !load.finished || load.cancelled) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        Path cachePath = load.cachePath;
        long revision = PlanningCatalogCacheFiles.revision(cachePath);
        long epoch = VirtualProvisionerRecipeRegistry.clientEpoch();
        Map<String, String> fingerprints = load.fingerprints;
        Set<String> types = Set.copyOf(completedTypes);
        Map<String, Set<String>> frozenGroups = Map.copyOf(groups);
        try
        {
            IO.execute(() -> {
                var snapshot = VirtualProvisionerRecipeRegistry.clientCatalogSnapshot(epoch);
                if (snapshot == null) return;
                var recipes = snapshot.stream()
                        .filter(value -> types.contains(value.descriptor().family())).toList();
                write(level.registryAccess(), cachePath, revision, fingerprints, types, frozenGroups, recipes);
            });
        }
        catch (RejectedExecutionException exception)
        { LOGGER.warn("{} client JEI cache save skipped reason=io_queue_full path={}", PREFIX, cachePath); }
    }

    private static Path path()
    {
        Path planning = ClientPlanningCatalogCache.path();
        return planning.getParent().resolveSibling("beyond_craftlines-jei-recipes-v1")
                .resolve(planning.getFileName());
    }

    static LoadJob loadAsync(Path cachePath, RegistryAccess registryAccess, Map<String, List<String>> sources)
    {
        LoadJob job = new LoadJob(cachePath, registryAccess, Map.copyOf(sources));
        try { job.future = IO.submit(job::read); }
        catch (RejectedExecutionException exception)
        {
            LOGGER.warn("{} client JEI cache unavailable reason=io_queue_full path={}", PREFIX, cachePath);
            job.finished = true;
        }
        return job;
    }

    static void write(RegistryAccess registryAccess, Path path, long revision, Map<String, String> fingerprints,
                      Set<String> types, Map<String, Set<String>> groups,
                      List<VirtualProvisionerRecipeRegistry.CachedDescriptor> recipes)
    {
        long started = System.nanoTime();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try
        {
            var counts = new PlanningCatalogCacheCounts();
            Files.createDirectories(path.getParent());
            try (var output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary)))))
            {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                Set<String> persisted = new TreeSet<>(types);
                persisted.retainAll(fingerprints.keySet());
                output.writeInt(persisted.size());
                for (String type : persisted)
                {
                    string(output, type, counts);
                    string(output, fingerprints.get(type), counts);
                    Set<String> values = groups.getOrDefault(type, Set.of());
                    output.writeInt(values.size());
                    for (String group : new TreeSet<>(values)) string(output, group, counts);
                }
                // All retained descriptors belong to a successfully scanned, completed category.
                List<VirtualProvisionerRecipeRegistry.CachedDescriptor> accepted = recipes.stream()
                        .filter(value -> persisted.contains(value.descriptor().family())).toList();
                output.writeInt(accepted.size());
                for (var value : accepted)
                {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("save interrupted");
                    writeDescriptor(output, registryAccess, value, counts);
                }
            }
            long bytes = Files.size(temporary);
            var result = PlanningCatalogCacheFiles.install(path, temporary, revision);
            LOGGER.info("{} client JEI cache save result={} path={} categories={} recipes={} bytes={} elapsedMs={}",
                    PREFIX, result, path, types.size(), recipes.size(), bytes,
                    (System.nanoTime() - started) / 1_000_000L);
        }
        catch (IOException | RuntimeException | LinkageError exception)
        {
            LOGGER.warn("{} client JEI cache save failed path={} error={}", PREFIX, path, exception.toString());
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }

    private static void writeDescriptor(DataOutputStream out, RegistryAccess registryAccess,
                                        VirtualProvisionerRecipeRegistry.CachedDescriptor value,
                                        PlanningCatalogCacheCounts counts) throws IOException
    {
        var descriptor = value.descriptor();
        string(out, value.id().toString(), counts);
        string(out, descriptor.family(), counts);
        ClientPlanningCatalogCache.writeKey(out, registryAccess, descriptor.output(), counts);
        out.writeLong(descriptor.outputAmount());
        out.writeInt(descriptor.inputs().size());
        for (var slot : descriptor.inputs())
        {
            string(out, slot.inputGroup(), counts);
            string(out, slot.use().kind().name(), counts);
            out.writeInt(slot.use().damagePerCraft());
            writeAmounts(out, registryAccess, slot.candidates(), counts);
        }
        writeAmounts(out, registryAccess, descriptor.byproducts(), counts);
        writeAmounts(out, registryAccess, descriptor.guaranteedByproducts(), counts);
    }

    private static void writeAmounts(DataOutputStream out, RegistryAccess registryAccess, List<KeyAmount> values,
                                     PlanningCatalogCacheCounts counts) throws IOException
    {
        out.writeInt(values.size());
        for (var value : values)
        {
            ClientPlanningCatalogCache.writeKey(out, registryAccess, value.key(), counts);
            out.writeLong(value.amount());
        }
    }

    private static void string(DataOutputStream out, String value, PlanningCatalogCacheCounts counts) throws IOException
    { ClientPlanningCatalogCache.writeString(out, value, counts); }

    static final class LoadJob
    {
        final Path cachePath;
        final RegistryAccess registryAccess;
        Map<String, List<String>> sources;
        final long epoch = VirtualProvisionerRecipeRegistry.clientEpoch();
        volatile Map<String, String> fingerprints = Map.of();
        volatile Restored result = new Restored(Set.of(), Map.of(), 0);
        volatile boolean finished;
        volatile boolean failed;
        volatile boolean cancelled;
        volatile int restoredRecipes;
        volatile int processedRecipes;
        volatile int totalRecipes;
        Future<?> future;
        private final Map<IStackKey<?>, IStackKey<?>> keys = new HashMap<>();
        private final PlanningCatalogCacheCounts counts = new PlanningCatalogCacheCounts();
        private final NbtAccounter nbt = NbtAccounter.unlimitedHeap();

        LoadJob(Path path, RegistryAccess registryAccess, Map<String, List<String>> sources)
        { this.cachePath = path; this.registryAccess = registryAccess; this.sources = sources; }

        void cancel()
        {
            cancelled = true;
            if (future != null) future.cancel(true);
        }

        void read()
        {
            long started = System.nanoTime();
            try
            {
                Map<String, String> current = new HashMap<>();
                sources.forEach((type, tokens) -> current.put(type, JeiRecipeSourceFingerprint.fingerprint(tokens)));
                fingerprints = Map.copyOf(current);
                sources = Map.of();
                if (cancelled) return;
                if (!Files.isRegularFile(cachePath))
                {
                    LOGGER.info("{} client JEI cache unavailable reason=file_missing path={}", PREFIX, cachePath);
                    return;
                }
                try (var input = new DataInputStream(new BufferedInputStream(
                        new GZIPInputStream(Files.newInputStream(cachePath)))))
                {
                    if (input.readInt() != MAGIC || input.readInt() != VERSION)
                        throw new IOException("incompatible JEI cache format");
                    Set<String> reusable = new HashSet<>();
                    Map<String, Set<String>> groups = new HashMap<>();
                    int categoryCount = count(input);
                    for (int i = 0; i < categoryCount; i++)
                    {
                        String type = string(input);
                        String fingerprint = string(input);
                        Set<String> values = new HashSet<>();
                        int groupCount = count(input);
                        for (int g = 0; g < groupCount; g++) values.add(string(input));
                        if (fingerprint.equals(current.get(type)))
                        {
                            reusable.add(type);
                            groups.put(type, Set.copyOf(values));
                        }
                        else LOGGER.info("{} client JEI category cache changed type={} reason=source_ids_changed", PREFIX, type);
                    }
                    totalRecipes = count(input);
                    for (int i = 0; i < totalRecipes && !cancelled; i++)
                    {
                        ResourceLocation id = ResourceLocation.parse(string(input));
                        String family = string(input);
                        boolean restore = reusable.contains(family);
                        IStackKey<?> output = key(input, restore);
                        long amount = input.readLong();
                        List<VirtualProvisionerRecipeRegistry.InputSlot> slots = new ArrayList<>();
                        int slotCount = count(input);
                        for (int slot = 0; slot < slotCount; slot++)
                        {
                            String group = string(input);
                            String kind = string(input);
                            int damage = input.readInt();
                            List<KeyAmount> candidates = amounts(input, restore);
                            if (restore) slots.add(new VirtualProvisionerRecipeRegistry.InputSlot(group, candidates,
                                    new VirtualInputUse(VirtualInputUse.Kind.valueOf(kind), damage)));
                        }
                        var byproducts = amounts(input, restore);
                        var guaranteed = amounts(input, restore);
                        if (restore)
                        {
                            var descriptor = new VirtualProvisionerRecipeRegistry.Descriptor(family, output, amount,
                                    slots, byproducts, guaranteed);
                            if (!id.equals(descriptor.id())) throw new IOException("JEI descriptor id does not match contents");
                            if (!VirtualProvisionerRecipeRegistry.restoreForClientCatalog(epoch, id, descriptor))
                            { cancelled = true; return; }
                            restoredRecipes++;
                        }
                        processedRecipes = i + 1;
                    }
                    if (cancelled) return;
                    if (input.read() != -1) throw new IOException("trailing JEI cache data");
                    result = new Restored(Set.copyOf(reusable), Map.copyOf(groups), restoredRecipes);
                    LOGGER.info("{} client JEI cache restored categories={} reloadCategories={} recipes={} path={} elapsedMs={}",
                            PREFIX, reusable.size(), current.size() - reusable.size(), restoredRecipes, cachePath,
                            (System.nanoTime() - started) / 1_000_000L);
                }
            }
            catch (IOException | RuntimeException | LinkageError exception)
            {
                failed = true;
                LOGGER.warn("{} client JEI cache read failed path={} error={}", PREFIX, cachePath, exception.toString());
            }
            finally { keys.clear(); finished = true; }
        }

        private int count(DataInputStream input) throws IOException
        { return ClientPlanningCatalogCache.nonNegative(input.readInt()); }
        private String string(DataInputStream input) throws IOException
        { return ClientPlanningCatalogCache.readString(input, counts); }
        private IStackKey<?> key(DataInputStream input, boolean decode) throws IOException
        {
            var encoded = ClientPlanningCatalogCache.readEncodedKey(input, counts, nbt);
            if (!decode) return null;
            IStackKey<?> key = StackKeyRegistry.getType(encoded.type()).deserializeNBT(encoded.nbt(), registryAccess);
            if (key == null || key.isEmpty()) throw new IOException("invalid JEI cached stack key");
            IStackKey<?> existing = keys.putIfAbsent(key, key);
            return existing == null ? key : existing;
        }
        private List<KeyAmount> amounts(DataInputStream input, boolean decode) throws IOException
        {
            int count = count(input);
            List<KeyAmount> result = new ArrayList<>();
            for (int i = 0; i < count; i++)
            {
                IStackKey<?> key = key(input, decode);
                long amount = input.readLong();
                if (decode) result.add(new KeyAmount(key, amount));
            }
            return result;
        }
    }
}
