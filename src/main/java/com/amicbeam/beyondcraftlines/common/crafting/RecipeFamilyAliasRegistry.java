package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Datapack-extensible JEI category aliases, rebuilt only after a resource reload. */
public final class RecipeFamilyAliasRegistry
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<String, Set<String>> aliases = Map.of();

    private RecipeFamilyAliasRegistry() {}

    public static Map<String, Set<String>> aliases() { return aliases; }

    public static synchronized void reload(ResourceManager resources)
    {
        Map<String, LinkedHashSet<String>> building = new HashMap<>();
        resources.listResources("recipe_type_aliases", path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                    try (Reader reader = resource.openAsReader())
                    { read(JsonParser.parseReader(reader).getAsJsonObject(), building); }
                    catch (Exception exception)
                    { LOGGER.error("Failed to load recipe type aliases from {}", id, exception); }
                });
        Map<String, Set<String>> frozen = new HashMap<>();
        building.forEach((type, families) -> frozen.put(type, Set.copyOf(families)));
        aliases = Map.copyOf(frozen);
        LOGGER.info("Loaded {} JEI recipe type alias entries", aliases.size());
    }

    static Map<String, Set<String>> parse(JsonObject object)
    {
        Map<String, LinkedHashSet<String>> result = new HashMap<>();
        read(object, result);
        Map<String, Set<String>> frozen = new HashMap<>();
        result.forEach((type, families) -> frozen.put(type, Set.copyOf(families)));
        return Map.copyOf(frozen);
    }

    private static void read(JsonObject object, Map<String, LinkedHashSet<String>> result)
    {
        if (object.has("jei_type")) add(result, object.get("jei_type").getAsString(),
                object.getAsJsonArray("recipe_types"));
        JsonObject mappings = object.has("mappings") ? object.getAsJsonObject("mappings") : null;
        if (mappings != null) for (Map.Entry<String, JsonElement> entry : mappings.entrySet())
            add(result, entry.getKey(), entry.getValue().getAsJsonArray());
    }

    private static void add(Map<String, LinkedHashSet<String>> result, String type, JsonArray families)
    {
        if (type == null || type.isBlank() || families == null) return;
        LinkedHashSet<String> target = result.computeIfAbsent(type, ignored -> new LinkedHashSet<>());
        families.forEach(value -> {
            String family = value.getAsString();
            if (!family.isBlank()) target.add(family);
        });
    }
}
