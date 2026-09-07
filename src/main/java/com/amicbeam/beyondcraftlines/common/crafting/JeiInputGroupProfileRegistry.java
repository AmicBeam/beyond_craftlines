package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Data-driven semantic grouping for JEI categories whose input slots have no names. */
public final class JeiInputGroupProfileRegistry
{
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_.$]{0,255}");
    private static final Pattern MEMBER_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,127}");
    private static volatile List<Profile> profiles = List.of();

    private JeiInputGroupProfileRegistry() {}

    public static synchronized void reload(ResourceManager resources)
    {
        List<Profile> loaded = new ArrayList<>();
        resources.listResources("jei_input_group_profiles", path -> path.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(128).forEach(entry -> {
                    try (Reader reader = entry.getValue().openAsReader())
                    {
                        Profile profile = parse(JsonParser.parseReader(reader).getAsJsonObject());
                        if (profile != null) loaded.add(profile);
                    }
                    catch (Exception exception)
                    { Logging.LOGGER.error("Failed to load JEI input group profile from {}", entry.getKey(), exception); }
                });
        profiles = List.copyOf(loaded);
        Logging.LOGGER.info("Loaded {} JEI input group profiles", profiles.size());
    }

    public static List<String> resolve(String jeiType, Object displayedRecipe, int inputSlotCount)
    {
        if (jeiType == null || displayedRecipe == null || inputSlotCount < 1 || inputSlotCount > 32)
            return List.of();
        Object recipe = unwrap(displayedRecipe);
        for (Profile profile : profiles)
        {
            if (!profile.jeiType().equals(jeiType) || !profile.matches(recipe)) continue;
            List<String> groups = resolve(profile, recipe, inputSlotCount);
            if (!groups.isEmpty()) return groups;
        }
        return List.of();
    }

    public static synchronized void clear()
    { profiles = List.of(); }

    static Profile parse(JsonObject object)
    {
        if (object == null || !object.has("jei_type")) return null;
        String jeiType = object.get("jei_type").getAsString();
        if (!RESOURCE_ID.matcher(jeiType).matches()) return null;
        Set<String> recipeClasses = strings(object.getAsJsonArray("recipe_classes"), CLASS_NAME, 32);
        JsonArray rawSections = object.getAsJsonArray("input_sections");
        if (rawSections == null || rawSections.isEmpty() || rawSections.size() > 32) return null;
        List<Section> sections = new ArrayList<>();
        for (JsonElement element : rawSections)
        {
            if (!element.isJsonObject()) return null;
            JsonObject raw = element.getAsJsonObject();
            if (!raw.has("group") || !raw.has("cardinality")) return null;
            String group = raw.get("group").getAsString();
            if (!JeiSlotInputGroup.isValid(group)) return null;
            Cardinality cardinality;
            try { cardinality = Cardinality.valueOf(raw.get("cardinality").getAsString().toUpperCase()); }
            catch (IllegalArgumentException exception) { return null; }
            List<String> members = List.copyOf(strings(raw.getAsJsonArray("members"), MEMBER_NAME, 8));
            if (members.isEmpty()) return null;
            sections.add(new Section(group, members, cardinality));
        }
        return new Profile(jeiType, recipeClasses, List.copyOf(sections));
    }

    static List<String> resolve(Profile profile, Object displayedRecipe, int inputSlotCount)
    {
        Object recipe = unwrap(displayedRecipe);
        if (profile == null || recipe == null || !profile.matches(recipe)) return List.of();
        List<String> groups = new ArrayList<>();
        for (Section section : profile.sections())
        {
            Object value = null;
            for (String member : section.members())
            {
                value = RecipeReflection.readPublicMember(recipe, member);
                if (value != null) break;
            }
            if (value == null) return List.of();
            int count = section.cardinality() == Cardinality.SINGLE ? 1 : collectionSize(value);
            if (count < 0 || groups.size() + count > inputSlotCount) return List.of();
            for (int index = 0; index < count; index++) groups.add(section.group());
        }
        return groups.size() == inputSlotCount ? List.copyOf(groups) : List.of();
    }

    private static Object unwrap(Object displayedRecipe)
    {
        String className = displayedRecipe.getClass().getName();
        if (!className.equals("net.minecraft.world.item.crafting.RecipeHolder")
                && !className.equals("com.amicbeam.beyondcraftlines.compat.crafting.RecipeHolder"))
            return displayedRecipe;
        Object value = RecipeReflection.readPublicMember(displayedRecipe, "value");
        return value == null ? displayedRecipe : value;
    }

    private static int collectionSize(Object value)
    {
        if (value instanceof Collection<?> collection) return collection.size();
        if (value.getClass().isArray()) return Array.getLength(value);
        if (!(value instanceof Iterable<?> iterable)) return -1;
        int count = 0;
        for (Object ignored : iterable) if (++count > 32) return -1;
        return count;
    }

    private static Set<String> strings(JsonArray values, Pattern pattern, int limit)
    {
        if (values == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonElement element : values)
        {
            if (result.size() >= limit || !element.isJsonPrimitive()) break;
            String value = element.getAsString();
            if (pattern.matcher(value).matches()) result.add(value);
        }
        return java.util.Collections.unmodifiableSet(result);
    }

    enum Cardinality { SINGLE, COLLECTION }

    record Section(String group, List<String> members, Cardinality cardinality) {}

    record Profile(String jeiType, Set<String> recipeClasses, List<Section> sections)
    {
        boolean matches(Object recipe)
        { return recipe != null && (recipeClasses.isEmpty() || recipeClasses.contains(recipe.getClass().getName())); }
    }

    private static final class Logging
    {
        private static final org.slf4j.Logger LOGGER =
                org.slf4j.LoggerFactory.getLogger(JeiInputGroupProfileRegistry.class);
    }
}
