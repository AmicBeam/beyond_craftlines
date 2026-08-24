package com.amicbeam.beyondcraftlines.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-global recipe-tree defaults shared by every save and multiplayer server. */
public final class ClientPlannerPreferences
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "beyond_craftlines-planner-preferences.json";
    private static final int MAX_ENTRIES = 4_096;

    private ClientPlannerPreferences() {}

    public static synchronized Snapshot load()
    {
        Path path = path();
        if (!Files.isRegularFile(path)) return Snapshot.EMPTY;
        try { return read(path); }
        catch (IOException | RuntimeException exception)
        {
            LOGGER.warn("Unable to read Craftlines client planner preferences", exception);
            return Snapshot.EMPTY;
        }
    }

    public static synchronized boolean setRecipe(String output, ResourceLocation recipe)
    {
        Snapshot old = load();
        LinkedHashMap<String, ResourceLocation> recipes = new LinkedHashMap<>(old.recipes());
        if (recipe == null) recipes.remove(output); else recipes.put(output, recipe);
        return write(recipes, old.ingredients());
    }

    public static synchronized boolean setIngredients(ResourceLocation recipe, Iterable<Integer> slots,
                                                      ResourceLocation item)
    {
        Snapshot old = load();
        LinkedHashMap<String, ResourceLocation> ingredients = new LinkedHashMap<>(old.ingredients());
        for (int slot : slots)
        {
            String key = ingredientKey(recipe, slot);
            if (item == null) ingredients.remove(key); else ingredients.put(key, item);
        }
        return write(old.recipes(), ingredients);
    }

    public static String ingredientKey(ResourceLocation recipe, int slot)
    { return recipe + "#" + slot; }

    private static Map<String, ResourceLocation> readMap(JsonObject object)
    {
        if (object == null) return Map.of();
        LinkedHashMap<String, ResourceLocation> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            if (result.size() >= MAX_ENTRIES) break;
            if (!entry.getValue().isJsonPrimitive()) continue;
            ResourceLocation value = ResourceLocation.tryParse(entry.getValue().getAsString());
            if (value != null) result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }

    private static Snapshot read(Path path) throws IOException
    {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return new Snapshot(readMap(root.getAsJsonObject("recipes")),
                    readMap(root.getAsJsonObject("ingredients")));
        }
    }

    private static boolean write(Map<String, ResourceLocation> recipes,
                                 Map<String, ResourceLocation> ingredients)
    {
        Path path = path();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("recipes", toJson(recipes));
            root.add("ingredients", toJson(ingredients));
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                 Writer writer = Channels.newWriter(channel, StandardCharsets.UTF_8))
            {
                GSON.toJson(root, writer);
                writer.flush();
                channel.force(true);
            }
            moveReplacing(temporary, path);
            return true;
        }
        catch (IOException exception)
        {
            LOGGER.warn("Unable to save Craftlines client planner preferences", exception);
            return false;
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored)
        { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static JsonObject toJson(Map<String, ResourceLocation> values)
    {
        JsonObject result = new JsonObject();
        values.entrySet().stream().limit(MAX_ENTRIES)
                .forEach(entry -> result.addProperty(entry.getKey(), entry.getValue().toString()));
        return result;
    }

    private static Path path()
    { return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME); }

    public record Snapshot(Map<String, ResourceLocation> recipes,
                           Map<String, ResourceLocation> ingredients)
    {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
    }
}
