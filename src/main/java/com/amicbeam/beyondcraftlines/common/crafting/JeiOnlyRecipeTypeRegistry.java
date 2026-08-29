package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Datapack-extensible JEI categories whose bounded layouts become virtual provisioner recipes. */
public final class JeiOnlyRecipeTypeRegistry
{
    private static final int MAX_TYPES = 1_024;
    private static final System.Logger LOGGER = System.getLogger(JeiOnlyRecipeTypeRegistry.class.getName());
    private static final Pattern RESOURCE_ID = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static volatile Set<String> datapackRecipeTypes = Set.of();
    private static volatile Set<String> recipeTypes = Set.of();
    private static volatile boolean serverRecipeValidationEnabled;

    private JeiOnlyRecipeTypeRegistry() {}

    public static Set<String> recipeTypes() { return recipeTypes; }
    public static Set<String> datapackRecipeTypes() { return datapackRecipeTypes; }

    public static boolean contains(Object type)
    { return type != null && recipeTypes.contains(type.toString()); }

    public static boolean containsDatapackType(Object type)
    { return type != null && datapackRecipeTypes.contains(type.toString()); }

    public static boolean isValidType(String type)
    { return type != null && type.length() <= 256 && RESOURCE_ID.matcher(type).matches(); }

    public static boolean serverRecipeValidationEnabled()
    { return serverRecipeValidationEnabled; }

    public static void setServerRecipeValidationEnabled(boolean enabled)
    { serverRecipeValidationEnabled = enabled; }

    public static synchronized void reload(ResourceManager resources)
    {
        LinkedHashSet<String> building = new LinkedHashSet<>();
        resources.listResources("jei_only_recipe_types", path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                    try (Reader reader = resource.openAsReader())
                    { parse(JsonParser.parseReader(reader).getAsJsonObject()).stream()
                            .limit(Math.max(0, MAX_TYPES - building.size())).forEach(building::add); }
                    catch (Exception exception)
                    { LOGGER.log(System.Logger.Level.ERROR,
                            "Failed to load JEI-only recipe types from " + id, exception); }
                });
        datapackRecipeTypes = Set.copyOf(building);
        recipeTypes = datapackRecipeTypes;
        LOGGER.log(System.Logger.Level.INFO,
                "Loaded " + datapackRecipeTypes.size() + " JEI-only recipe types");
    }

    public static synchronized void applySyncedTypes(Collection<String> types)
    { recipeTypes = sanitize(types); }

    public static synchronized void enableSyncedType(Object type)
    {
        String value = String.valueOf(type);
        if (!isValidType(value) || (recipeTypes.size() >= MAX_TYPES && !recipeTypes.contains(value))) return;
        LinkedHashSet<String> updated = new LinkedHashSet<>(recipeTypes);
        updated.add(value);
        recipeTypes = Set.copyOf(updated);
    }

    static Set<String> parse(JsonObject object)
    {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (object.has("jei_type")) add(values, object.get("jei_type"));
        if (object.has("jei_types"))
            object.getAsJsonArray("jei_types").forEach(value -> add(values, value));
        return Set.copyOf(values);
    }

    private static Set<String> sanitize(Collection<String> types)
    {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (types != null) types.forEach(type -> add(values, type));
        return Set.copyOf(values);
    }

    private static void add(Set<String> target, JsonElement value)
    {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
            add(target, value.getAsString());
    }

    private static void add(Set<String> target, String value)
    {
        if (target.size() < MAX_TYPES && isValidType(value))
            target.add(value);
    }
}
