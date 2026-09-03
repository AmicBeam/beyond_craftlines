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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned, bounded client cache for immutable planning catalogs. */
final class ClientPlanningCatalogCache
{
    static final int MAGIC = 0x42434C43;
    static final int VERSION = 2;
    private static final long MAX_FILE_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_TOTAL_NBT_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_STRING_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_ENTRIES = 8_000_000L;
    private static final int MAX_RECIPES = 200_000;
    private static final int MAX_SLOTS = 128;
    private static final int MAX_CANDIDATES = 200_000;
    private static final int MAX_STRING_BYTES = 1_048_576;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), runnable -> {
        Thread thread = new Thread(runnable, "beyond-craftlines-planning-cache");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    private ClientPlanningCatalogCache() {}

    static LoadJob loadAsync(List<String> holderIds, long generation)
    { return loadAsync(path(), holderIds, generation); }

    static LoadJob loadAsync(Path cachePath, List<String> holderIds, long generation)
    {
        LoadJob job = new LoadJob(cachePath, generation, List.copyOf(holderIds));
        job.start();
        return job;
    }

    static void save(Level level, List<String> holderIds, ClientRecipePlanner.Catalog catalog)
    {
        if (!cacheable(catalog)) return;
        String fingerprint = fingerprint(holderIds);
        try { IO.execute(() -> write(level, fingerprint, catalog)); }
        catch (RejectedExecutionException exception)
        { LOGGER.warn("{} client planning cache save skipped because the bounded I/O queue is full",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX); }
    }

    private static boolean cacheable(ClientRecipePlanner.Catalog catalog)
    {
        if (catalog.recipes().size() > MAX_RECIPES) return false;
        long entries = catalog.recipes().size();
        for (ClientRecipePlanner.Recipe recipe : catalog.recipes())
        {
            if (recipe.slots().size() > MAX_SLOTS) return false;
            entries += recipe.slots().size();
            for (ClientRecipePlanner.Slot slot : recipe.slots())
            {
                if (slot.candidates().size() > MAX_CANDIDATES) return false;
                entries += slot.candidates().size();
                if (entries > MAX_TOTAL_ENTRIES) return false;
            }
        }
        return true;
    }

    private static void write(Level level, String fingerprint, ClientRecipePlanner.Catalog catalog)
    {
        Path path = path();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(path.getParent());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary)))))
            {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                writeString(output, fingerprint);
                output.writeInt(catalog.recipes().size());
                for (ClientRecipePlanner.Recipe recipe : catalog.recipes()) writeRecipe(output, level, recipe);
            }
            if (Files.size(temporary) > MAX_FILE_BYTES) Files.deleteIfExists(temporary);
            else moveReplacing(temporary, path);
        }
        catch (IOException | RuntimeException | LinkageError exception)
        {
            try { Files.deleteIfExists(temporary); }
            catch (IOException ignored) {}
        }
    }

    private static void writeRecipe(DataOutputStream output, Level level,
                                    ClientRecipePlanner.Recipe recipe) throws IOException
    {
        writeString(output, recipe.id().toString());
        writeString(output, recipe.family());
        writeKey(output, level, recipe.output());
        output.writeLong(recipe.outputCount());
        writeString(output, recipe.outputMatch().name());
        output.writeInt(recipe.slots().size());
        for (ClientRecipePlanner.Slot slot : recipe.slots())
        {
            output.writeInt(slot.index());
            writeString(output, slot.use().kind().name());
            output.writeInt(slot.use().damagePerCraft());
            output.writeInt(slot.candidates().size());
            for (ClientRecipePlanner.Candidate candidate : slot.candidates())
            {
                writeKey(output, level, candidate.key());
                output.writeLong(candidate.count());
                writeString(output, candidate.explicitSelectionItem() == null
                        ? "" : candidate.explicitSelectionItem().toString());
                writeString(output, candidate.explicitSelection() == null ? "" : candidate.explicitSelection());
            }
        }
    }

    private static EncodedRecipe readEncodedRecipe(DataInputStream input, ReadBudget budget,
                                                   NbtAccounter nbtBudget) throws IOException
    {
        ResourceLocation id = ResourceLocation.tryParse(readString(input, budget));
        String family = readString(input, budget);
        EncodedKey output = readEncodedKey(input, budget, nbtBudget);
        long outputCount = input.readLong();
        RecipeIoProfileRegistry.OutputMatchSemantics outputMatch =
                RecipeIoProfileRegistry.OutputMatchSemantics.valueOf(readString(input, budget));
        int slotCount = bounded(input.readInt(), MAX_SLOTS);
        budget.addEntries(slotCount);
        List<EncodedSlot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++)
        {
            int slotIndex = input.readInt();
            VirtualInputUse.Kind kind = VirtualInputUse.Kind.valueOf(readString(input, budget));
            VirtualInputUse use = new VirtualInputUse(kind, input.readInt());
            int candidateCount = bounded(input.readInt(), MAX_CANDIDATES);
            budget.addEntries(candidateCount);
            List<EncodedCandidate> candidates = new ArrayList<>(candidateCount);
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
        return new EncodedRecipe(id, family, output, outputCount, outputMatch, List.copyOf(slots));
    }

    private static void writeKey(DataOutputStream output, Level level, IStackKey<?> key) throws IOException
    {
        writeString(output, key.getTypeId().toString());
        NbtIo.write(key.serializeNBT(level.registryAccess()), output);
    }

    private static EncodedKey readEncodedKey(DataInputStream input, ReadBudget budget,
                                             NbtAccounter nbtBudget) throws IOException
    {
        ResourceLocation type = ResourceLocation.tryParse(readString(input, budget));
        CompoundTag encoded = NbtIo.read(input, nbtBudget);
        if (type == null || encoded == null) throw new IOException("invalid cached stack key");
        return new EncodedKey(type, encoded);
    }

    private static int bounded(int value, int maximum) throws IOException
    {
        if (value < 0 || value > maximum) throw new IOException("invalid cache collection size");
        return value;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("cache string is too large");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, ReadBudget budget) throws IOException
    {
        int length = bounded(input.readInt(), MAX_STRING_BYTES);
        budget.addStringBytes(length);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new java.io.EOFException("truncated cache string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String fingerprint(List<String> holderIds)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            holderIds.forEach(id -> {
                digest.update(id.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return java.util.HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException impossible)
        { throw new IllegalStateException(impossible); }
    }

    private static Path path()
    { return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
            .resolve("beyond_craftlines-planning-catalog-v2.dat"); }

    private static void moveReplacing(Path source, Path target) throws IOException
    {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored)
        { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    static final class LoadJob
    {
        private final long generation;
        private final Path cachePath;
        private final List<String> holderIds;
        private final ArrayBlockingQueue<EncodedRecipe> queue = new ArrayBlockingQueue<>(2);
        private final List<ClientRecipePlanner.Recipe> decoded = new ArrayList<>();
        private final java.util.Map<IStackKey<?>, IStackKey<?>> decodedKeys = new java.util.HashMap<>();
        private volatile State state = State.QUEUED;
        private volatile int totalRecipes;
        private volatile Future<?> future;
        private volatile boolean cancelled;
        private volatile long ioNanos;
        private volatile long headerNanos;
        private volatile long parseNanos;
        private long decodeNanos;
        private DecodeCursor decoder;
        private ClientRecipePlanner.Catalog catalog;

        private LoadJob(Path cachePath, long generation, List<String> holderIds)
        { this.cachePath = cachePath; this.generation = generation; this.holderIds = holderIds; }

        private void start()
        {
            try { future = IO.submit(this::read); }
            catch (RejectedExecutionException exception) { state = State.MISS; }
        }

        private void read()
        {
            long started = System.nanoTime();
            try
            {
                if (!Files.isRegularFile(cachePath) || Files.size(cachePath) > MAX_FILE_BYTES)
                { state = State.MISS; return; }
                try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                        new GZIPInputStream(Files.newInputStream(cachePath)))))
                {
                    ReadBudget budget = new ReadBudget();
                    NbtAccounter nbtBudget = NbtAccounter.create(MAX_TOTAL_NBT_BYTES);
                    if (input.readInt() != MAGIC || input.readInt() != VERSION
                            || !fingerprint(holderIds).equals(readString(input, budget)))
                    { state = State.MISS; return; }
                    totalRecipes = bounded(input.readInt(), MAX_RECIPES);
                    budget.addEntries(totalRecipes);
                    headerNanos = System.nanoTime() - started;
                    state = State.READING;
                    for (int i = 0; i < totalRecipes && !cancelled; i++)
                    {
                        long parseStarted = System.nanoTime();
                        EncodedRecipe encoded = readEncodedRecipe(input, budget, nbtBudget);
                        parseNanos += System.nanoTime() - parseStarted;
                        queue.put(encoded);
                    }
                    state = cancelled ? State.CANCELLED : State.EOF;
                }
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                state = State.CANCELLED;
            }
            catch (IOException | RuntimeException | LinkageError exception)
            {
                state = State.FAILED;
                LOGGER.warn("{} client planning cache read failed path={} error={}",
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                        cachePath, exception.toString());
            }
            finally { ioNanos = System.nanoTime() - started; }
        }

        synchronized void advance(Level level, long timeBudgetNanos)
        {
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
                ClientRecipePlanner.Recipe recipe = decoder.advance(level);
                if (recipe != null)
                {
                    decoded.add(recipe);
                    decoder = null;
                }
                processed++;
            }
            decodeNanos += System.nanoTime() - started;
            if (state == State.EOF && queue.isEmpty() && decoder == null && decoded.size() == totalRecipes)
            {
                catalog = new ClientRecipePlanner.Catalog(decoded);
                decodedKeys.clear();
            }
        }

        private IStackKey<?> decodeKey(EncodedKey encoded, Level level)
        {
            IStackKey<?> key = StackKeyRegistry.getType(encoded.type())
                    .deserializeNBT(encoded.nbt(), level.registryAccess());
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("invalid cached stack key");
            IStackKey<?> existing = decodedKeys.putIfAbsent(key, key);
            return existing == null ? key : existing;
        }

        synchronized void cancel()
        {
            cancelled = true;
            state = State.CANCELLED;
            queue.clear();
            decoded.clear();
            decodedKeys.clear();
            decoder = null;
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
        int completedRecipes() { return decoded.size(); }
        int totalRecipes() { return totalRecipes > 0 ? totalRecipes : holderIds.size(); }
        int queueDepth() { return queue.size(); }
        long ioMillis() { return ioNanos / 1_000_000L; }
        long headerMillis() { return headerNanos / 1_000_000L; }
        long parseMillis() { return parseNanos / 1_000_000L; }
        long decodeMillis() { return decodeNanos / 1_000_000L; }
        String stateName() { return state.name().toLowerCase(java.util.Locale.ROOT); }

        private final class DecodeCursor
        {
            private final EncodedRecipe encoded;
            private final List<ClientRecipePlanner.Slot> slots;
            private IStackKey<?> output;
            private int slotIndex;
            private int candidateIndex;
            private List<ClientRecipePlanner.Candidate> candidates;

            private DecodeCursor(EncodedRecipe encoded)
            { this.encoded = encoded; this.slots = new ArrayList<>(encoded.slots().size()); }

            private ClientRecipePlanner.Recipe advance(Level level)
            {
                if (output == null)
                {
                    output = decodeKey(encoded.output(), level);
                    return null;
                }
                if (slotIndex < encoded.slots().size())
                {
                    EncodedSlot slot = encoded.slots().get(slotIndex);
                    if (candidates == null) candidates = new ArrayList<>(slot.candidates().size());
                    if (candidateIndex < slot.candidates().size())
                    {
                        EncodedCandidate candidate = slot.candidates().get(candidateIndex++);
                        IStackKey<?> key = decodeKey(candidate.key(), level);
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
                        encoded.outputCount(), encoded.outputMatch(), slots);
            }
        }
    }

    private enum State { QUEUED, READING, EOF, MISS, FAILED, CANCELLED }
    private record EncodedKey(ResourceLocation type, CompoundTag nbt) {}
    private record EncodedCandidate(EncodedKey key, long count, ResourceLocation selectionItem, String selection) {}
    private record EncodedSlot(int index, List<EncodedCandidate> candidates, VirtualInputUse use) {}
    private record EncodedRecipe(ResourceLocation id, String family, EncodedKey output, long outputCount,
                                 RecipeIoProfileRegistry.OutputMatchSemantics outputMatch,
                                 List<EncodedSlot> slots) {}

    private static final class ReadBudget
    {
        private long stringBytes;
        private long entries;
        void addStringBytes(int count) throws IOException
        {
            stringBytes += count;
            if (stringBytes > MAX_TOTAL_STRING_BYTES) throw new IOException("cache text budget exceeded");
        }
        void addEntries(int count) throws IOException
        {
            entries += count;
            if (entries > MAX_TOTAL_ENTRIES) throw new IOException("cache entry budget exceeded");
        }
    }
}
