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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned, bounded client cache for immutable planning catalogs. */
final class ClientPlanningCatalogCache
{
    private static final int MAGIC = 0x42434C43;
    private static final int VERSION = 1;
    private static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_NBT_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_TOTAL_STRING_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_TOTAL_ENTRIES = 2_000_000L;
    private static final int MAX_RECIPES = 200_000;
    private static final int MAX_SLOTS = 128;
    private static final int MAX_CANDIDATES = 1_024;
    private static final int MAX_STRING_BYTES = 1_048_576;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "beyond-craftlines-planning-cache");
        thread.setDaemon(true);
        return thread;
    });

    private ClientPlanningCatalogCache() {}

    static ClientRecipePlanner.Catalog load(Level level, List<String> holderIds)
    {
        Path path = path();
        try
        {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES) return null;
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(path)))))
            {
                ReadBudget budget = new ReadBudget();
                NbtAccounter nbtBudget = NbtAccounter.create(MAX_TOTAL_NBT_BYTES);
                if (input.readInt() != MAGIC || input.readInt() != VERSION
                        || !fingerprint(holderIds).equals(readString(input, budget))) return null;
                int recipeCount = bounded(input.readInt(), MAX_RECIPES);
                budget.addEntries(recipeCount);
                List<ClientRecipePlanner.Recipe> recipes = new ArrayList<>(recipeCount);
                for (int i = 0; i < recipeCount; i++) recipes.add(readRecipe(input, level, budget, nbtBudget));
                return new ClientRecipePlanner.Catalog(recipes);
            }
        }
        catch (IOException | RuntimeException | LinkageError exception)
        {
            return null;
        }
    }

    static void save(Level level, List<String> holderIds, ClientRecipePlanner.Catalog catalog)
    {
        if (catalog.recipes().size() > MAX_RECIPES) return;
        String fingerprint = fingerprint(holderIds);
        IO.execute(() -> write(level, fingerprint, catalog));
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
                writeString(output, candidate.selectionItem() == null ? "" : candidate.selectionItem().toString());
                writeString(output, candidate.selection());
            }
        }
    }

    private static ClientRecipePlanner.Recipe readRecipe(DataInputStream input, Level level,
                                                         ReadBudget budget, NbtAccounter nbtBudget) throws IOException
    {
        ResourceLocation id = ResourceLocation.tryParse(readString(input, budget));
        String family = readString(input, budget);
        IStackKey<?> output = readKey(input, level, budget, nbtBudget);
        long outputCount = input.readLong();
        RecipeIoProfileRegistry.OutputMatchSemantics outputMatch =
                RecipeIoProfileRegistry.OutputMatchSemantics.valueOf(readString(input, budget));
        int slotCount = bounded(input.readInt(), MAX_SLOTS);
        budget.addEntries(slotCount);
        List<ClientRecipePlanner.Slot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++)
        {
            int slotIndex = input.readInt();
            VirtualInputUse.Kind kind = VirtualInputUse.Kind.valueOf(readString(input, budget));
            VirtualInputUse use = new VirtualInputUse(kind, input.readInt());
            int candidateCount = bounded(input.readInt(), MAX_CANDIDATES);
            budget.addEntries(candidateCount);
            List<ClientRecipePlanner.Candidate> candidates = new ArrayList<>(candidateCount);
            for (int candidate = 0; candidate < candidateCount; candidate++)
            {
                IStackKey<?> key = readKey(input, level, budget, nbtBudget);
                long count = input.readLong();
                String selectedItem = readString(input, budget);
                ResourceLocation item = selectedItem.isEmpty() ? null : ResourceLocation.tryParse(selectedItem);
                candidates.add(new ClientRecipePlanner.Candidate(key, count, item, readString(input, budget)));
            }
            slots.add(new ClientRecipePlanner.Slot(slotIndex, candidates, use));
        }
        if (id == null || output == null || output.isEmpty()) throw new IOException("invalid cached recipe");
        return new ClientRecipePlanner.Recipe(id, family, output, outputCount, outputMatch, slots);
    }

    private static void writeKey(DataOutputStream output, Level level, IStackKey<?> key) throws IOException
    {
        writeString(output, key.getTypeId().toString());
        NbtIo.write(key.serializeNBT(level.registryAccess()), output);
    }

    private static IStackKey<?> readKey(DataInputStream input, Level level,
                                        ReadBudget budget, NbtAccounter nbtBudget) throws IOException
    {
        ResourceLocation type = ResourceLocation.tryParse(readString(input, budget));
        CompoundTag encoded = NbtIo.read(input, nbtBudget);
        if (type == null || encoded == null) throw new IOException("invalid cached stack key");
        return StackKeyRegistry.getType(type).deserializeNBT(encoded, level.registryAccess());
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
            .resolve("beyond_craftlines-planning-catalog-v1.dat"); }

    private static void moveReplacing(Path source, Path target) throws IOException
    {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored)
        { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

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
