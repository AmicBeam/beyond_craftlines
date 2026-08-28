package com.amicbeam.beyondcraftlines.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.amicbeam.beyondcraftlines.common.runtime.OrderOutputDestination;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static Snapshot load()
    {
        Path path = path();
        if (!Files.isRegularFile(path)) return Snapshot.EMPTY;
        try (Reader reader = Files.newBufferedReader(path))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return new Snapshot(readMap(root.getAsJsonObject("recipes")),
                    readMap(root.getAsJsonObject("ingredients")),
                    OrderOutputDestination.byId(root.has("output_destination")
                            ? root.get("output_destination").getAsString() : "network"));
        }
        catch (IOException | RuntimeException exception)
        {
            LOGGER.warn("Unable to read Craftlines client planner preferences", exception);
            return Snapshot.EMPTY;
        }
    }

    public static boolean setRecipe(String output, Identifier recipe)
    {
        Snapshot old = load();
        LinkedHashMap<String, Identifier> recipes = new LinkedHashMap<>(old.recipes());
        if (recipe == null) recipes.remove(output); else recipes.put(output, recipe);
        return write(recipes, old.ingredients(), old.outputDestination());
    }

    public static boolean clearRecipe(String output, Identifier legacyOutput)
    {
        Snapshot old = load();
        LinkedHashMap<String, Identifier> recipes = new LinkedHashMap<>(old.recipes());
        recipes.remove(output);
        if (legacyOutput != null) recipes.remove(legacyOutput.toString());
        return write(recipes, old.ingredients(), old.outputDestination());
    }

    public static boolean setIngredients(Identifier recipe, Iterable<Integer> slots, Identifier item)
    {
        Snapshot old = load();
        LinkedHashMap<String, Identifier> ingredients = new LinkedHashMap<>(old.ingredients());
        for (int slot : slots)
        {
            String key = ingredientKey(recipe, slot);
            if (item == null) ingredients.remove(key); else ingredients.put(key, item);
        }
        return write(old.recipes(), ingredients, old.outputDestination());
    }

    public static boolean setOutputDestination(OrderOutputDestination destination)
    {
        Snapshot old = load();
        return write(old.recipes(), old.ingredients(), destination);
    }

    public static String ingredientKey(Identifier recipe, int slot)
    { return recipe + "#" + slot; }

    private static Map<String, Identifier> readMap(JsonObject object)
    {
        if (object == null) return Map.of();
        LinkedHashMap<String, Identifier> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            if (result.size() >= MAX_ENTRIES) break;
            if (!entry.getValue().isJsonPrimitive()) continue;
            Identifier value = Identifier.tryParse(entry.getValue().getAsString());
            if (value != null) result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }

    private static boolean write(Map<String, Identifier> recipes,
                                 Map<String, Identifier> ingredients,
                                 OrderOutputDestination outputDestination)
    {
        Path path = path();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("output_destination", outputDestination.id());
            root.add("recipes", toJson(recipes));
            root.add("ingredients", toJson(ingredients));
            try (Writer writer = Files.newBufferedWriter(temporary)) { GSON.toJson(root, writer); }
            try
            {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
        catch (IOException exception)
        {
            LOGGER.warn("Unable to save Craftlines client planner preferences", exception);
            return false;
        }
    }

    private static JsonObject toJson(Map<String, Identifier> values)
    {
        JsonObject result = new JsonObject();
        values.entrySet().stream().limit(MAX_ENTRIES)
                .forEach(entry -> result.addProperty(entry.getKey(), entry.getValue().toString()));
        return result;
    }

    private static Path path()
    { return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME); }

    public record Snapshot(Map<String, Identifier> recipes,
                           Map<String, Identifier> ingredients,
                           OrderOutputDestination outputDestination)
    {
        private static final Snapshot EMPTY = new Snapshot(
                Map.of(), Map.of(), OrderOutputDestination.NETWORK);
    }
}
