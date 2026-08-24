package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Datapack-controlled public field/accessor whitelist, scoped by authoritative server RecipeType. */
public final class RecipeFieldWhitelistRegistry
{
    private static final Pattern MEMBER_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,127}");
    private static volatile Map<String, Fields> entries = Map.of();

    private RecipeFieldWhitelistRegistry() {}

    public static List<String> inputMembers(Recipe<?> recipe, List<String> defaults)
    { return members(recipe, defaults, true); }

    public static List<String> outputMembers(Object recipe, List<String> defaults)
    { return recipe instanceof Recipe<?> typed ? members(typed, defaults, false) : defaults; }

    public static List<String> encodedEntries()
    {
        return entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(128)
                .map(entry -> entry.getKey() + "|" + (entry.getValue().includeDefaults() ? "1" : "0")
                        + "|" + String.join(",", entry.getValue().inputFields().stream().sorted().toList())
                        + "|" + String.join(",", entry.getValue().outputFields().stream().sorted().toList()))
                .toList();
    }

    public static synchronized void applySyncedEntries(Collection<String> encoded)
    {
        Map<String, MutableFields> building = new HashMap<>();
        for (String value : encoded.stream().limit(128).toList())
        {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 4 || parts[0].isBlank()) continue;
            MutableFields fields = building.computeIfAbsent(parts[0], ignored -> new MutableFields());
            fields.includeDefaults &= "1".equals(parts[1]);
            addEncoded(fields.inputFields, parts[2]);
            addEncoded(fields.outputFields, parts[3]);
        }
        entries = freeze(building);
        RecipePlanningService.clearRecipeCache();
    }

    private static List<String> members(Recipe<?> recipe, List<String> defaults, boolean input)
    {
        String type = RecipePlanningService.family(recipe.getType());
        Fields fields = type == null ? null : entries.get(type);
        if (fields == null) return defaults;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (fields.includeDefaults()) result.addAll(defaults);
        result.addAll(input ? fields.inputFields() : fields.outputFields());
        return List.copyOf(result);
    }

    public static synchronized void reload(ResourceManager resources)
    {
        Map<String, MutableFields> building = new HashMap<>();
        resources.listResources("recipe_field_whitelists", path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                    try (Reader reader = resource.openAsReader())
                    { read(JsonParser.parseReader(reader).getAsJsonObject(), building); }
                    catch (Exception exception)
                    { Logging.LOGGER.error("Failed to load recipe field whitelist from {}", id, exception); }
                });
        entries = freeze(building);
        Logging.LOGGER.info("Loaded {} recipe field whitelist entries", entries.size());
    }

    static Map<String, Fields> parse(JsonObject object)
    {
        Map<String, MutableFields> result = new HashMap<>();
        read(object, result);
        return freeze(result);
    }

    private static void read(JsonObject object, Map<String, MutableFields> result)
    {
        List<String> types = new ArrayList<>();
        if (object.has("recipe_type")) types.add(object.get("recipe_type").getAsString());
        if (object.has("recipe_types")) object.getAsJsonArray("recipe_types")
                .forEach(value -> types.add(value.getAsString()));
        boolean includeDefaults = !object.has("include_defaults")
                || object.get("include_defaults").getAsBoolean();
        for (String type : types.stream().distinct().limit(128).toList())
        {
            if (type == null || type.isBlank()) continue;
            MutableFields fields = result.computeIfAbsent(type, ignored -> new MutableFields());
            fields.includeDefaults &= includeDefaults;
            add(fields.inputFields, object.getAsJsonArray("input_fields"));
            add(fields.outputFields, object.getAsJsonArray("output_fields"));
        }
    }

    private static void add(LinkedHashSet<String> target, JsonArray values)
    {
        if (values == null) return;
        for (JsonElement value : values)
        {
            if (target.size() >= 32) break;
            String name = value.getAsString();
            if (MEMBER_NAME.matcher(name).matches()) target.add(name);
        }
    }

    private static void addEncoded(LinkedHashSet<String> target, String values)
    {
        if (values.isBlank()) return;
        for (String name : values.split(",", -1))
        {
            if (target.size() >= 32) break;
            if (MEMBER_NAME.matcher(name).matches()) target.add(name);
        }
    }

    private static Map<String, Fields> freeze(Map<String, MutableFields> source)
    {
        Map<String, Fields> frozen = new HashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(128).forEach(entry -> {
            MutableFields fields = entry.getValue();
            frozen.put(entry.getKey(), new Fields(fields.includeDefaults,
                    Set.copyOf(fields.inputFields), Set.copyOf(fields.outputFields)));
        });
        return Map.copyOf(frozen);
    }

    private static final class MutableFields
    {
        private boolean includeDefaults = true;
        private final LinkedHashSet<String> inputFields = new LinkedHashSet<>();
        private final LinkedHashSet<String> outputFields = new LinkedHashSet<>();
    }

    private static final class Logging
    {
        private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
                RecipeFieldWhitelistRegistry.class);
    }

    public record Fields(boolean includeDefaults, Set<String> inputFields, Set<String> outputFields) {}
}
