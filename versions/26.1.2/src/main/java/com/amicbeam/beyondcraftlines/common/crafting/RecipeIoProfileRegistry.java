package com.amicbeam.beyondcraftlines.common.crafting;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/** Server-authoritative, datapack-defined structural recipe compatibility profiles. */
public final class RecipeIoProfileRegistry
{
    private static final Pattern MEMBER_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,127}");
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_.$]{0,255}");
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern STACK_TYPE = Pattern.compile("[a-z0-9_./-]+(?::[a-z0-9/._-]+)?");
    private static final Gson GSON = new Gson();
    private static volatile List<Entry> entries = List.of();

    private RecipeIoProfileRegistry() {}

    public static List<String> inputMembers(Object recipe)
    { return resolved(recipe).inputFields(); }

    public static List<String> outputMembers(Object recipe)
    { return resolved(recipe).outputFields(); }

    static List<String> representationMembers(Object recipe)
    { return resolved(recipe).representationFields(); }

    static List<CountedWrapper> countedWrappers(Object recipe)
    { return resolved(recipe).countedWrappers(); }

    static List<String> structuralWrapperMembers(Object recipe)
    { return resolved(recipe).structuralWrapperFields(); }

    static List<String> outputWrapperMembers(Object recipe)
    { return resolved(recipe).outputWrapperFields(); }

    static List<DirectionRule> directionRules(Object recipe)
    { return resolved(recipe).directions(); }

    public static InputCountSemantics inputCountSemantics(Recipe<?> recipe, String member)
    { return resolved(recipe).inputCountSemantics().getOrDefault(member, InputCountSemantics.REQUIRED); }

    static boolean distinctInputMember(Object recipe, String member)
    { return resolved(recipe).distinctInputFields().contains(member); }

    public static List<OutputMapping> outputMappings(Recipe<?> recipe)
    { return resolved(recipe).outputMappings(); }

    static OutputMatchSemantics outputMatchSemantics(Recipe<?> recipe)
    { return resolved(recipe).outputMatch(); }

    public static boolean outputMatches(Recipe<?> recipe, IStackKey<?> requested, IStackKey<?> declared)
    {
        return outputMatches(resolved(recipe).outputMatch(), requested, declared,
                StackKeyMatch::exact, (left, right) -> left.isSame(right) || right.isSame(left));
    }

    static <T> boolean outputMatches(OutputMatchSemantics semantics, T requested, T declared,
                                     BiPredicate<T, T> exact, BiPredicate<T, T> sameResource)
    { return exact.test(requested, declared)
            || semantics == OutputMatchSemantics.SAME_RESOURCE && sameResource.test(requested, declared); }

    static long inputMultiplier(Object recipe, String member)
    {
        long result = 1;
        for (MultiplierRule rule : resolved(recipe).multipliers())
        {
            if (!rule.inputFields().contains(member) || !matchesClass(recipe, rule.recipeClasses(),
                    rule.recipeClassPrefixes())) continue;
            if (!rule.whenBooleanField().isBlank())
            {
                Object condition = RecipeReflection.readPublicMember(recipe, rule.whenBooleanField());
                if (!(condition instanceof Boolean enabled) || !enabled) continue;
            }
            result = SaturatingLongMath.multiply(result, rule.factor());
        }
        return result;
    }

    public static List<String> encodedEntries()
    { return entries.stream().limit(128).map(Entry::encoded).toList(); }

    public static synchronized void applySyncedEntries(Collection<String> encoded)
    {
        entries = parseEncoded(encoded);
        RecipePlanningService.clearRecipeCache();
    }

    static synchronized void applyEntriesForTests(Collection<String> encoded)
    { entries = parseEncoded(encoded); }

    private static List<Entry> parseEncoded(Collection<String> encoded)
    {
        List<Entry> parsed = new ArrayList<>();
        for (String value : encoded.stream().limit(128).toList())
        {
            if (value == null || value.isBlank() || value.length() > 10_000) continue;
            try
            {
                JsonObject object = JsonParser.parseString(value).getAsJsonObject();
                Profile profile = parse(object);
                if (profile != null) parsed.add(new Entry(profile, GSON.toJson(object)));
            }
            catch (RuntimeException ignored) {}
        }
        return List.copyOf(parsed);
    }

    public static synchronized void reload(ResourceManager resources)
    {
        List<Entry> parsed = new ArrayList<>();
        resources.listResources("recipe_io_profiles", path -> path.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(128).forEach(resourceEntry -> {
                    try (Reader reader = resourceEntry.getValue().openAsReader())
                    {
                        JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                        Profile profile = parse(object);
                        if (profile != null) parsed.add(new Entry(profile, GSON.toJson(object)));
                    }
                    catch (Exception exception)
                    { Logging.LOGGER.error("Failed to load recipe I/O profile from {}", resourceEntry.getKey(), exception); }
                });
        entries = List.copyOf(parsed);
        RecipePlanningService.clearRecipeCache();
        Logging.LOGGER.info("Loaded {} recipe I/O profiles", entries.size());
    }

    static Profile parse(JsonObject object)
    {
        Set<String> recipeTypes = strings(object.getAsJsonArray("recipe_types"), RESOURCE_ID, 128);
        if (object.has("recipe_type")) recipeTypes = with(recipeTypes,
                valid(object.get("recipe_type").getAsString(), RESOURCE_ID));
        Set<String> recipeClasses = strings(object.getAsJsonArray("recipe_classes"), CLASS_NAME, 64);
        Set<String> recipeClassPrefixes = strings(object.getAsJsonArray("recipe_class_prefixes"), CLASS_NAME, 64);
        boolean includeDefaults = !object.has("include_defaults") || object.get("include_defaults").getAsBoolean();
        Set<String> inputFields = strings(object.getAsJsonArray("input_fields"), MEMBER_NAME, 128);
        Set<String> distinctInputFields = strings(
                object.getAsJsonArray("distinct_input_fields"), MEMBER_NAME, 128);
        distinctInputFields = distinctInputFields.stream().filter(inputFields::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> outputFields = strings(object.getAsJsonArray("output_fields"), MEMBER_NAME, 64);
        OutputMatchSemantics outputMatch = OutputMatchSemantics.parse(string(object, "output_match"));
        Set<String> representationFields = strings(object.getAsJsonArray("representation_fields"), MEMBER_NAME, 64);
        Set<String> structuralWrappers = strings(object.getAsJsonArray("structural_wrapper_fields"), MEMBER_NAME, 32);
        Set<String> outputWrappers = strings(object.getAsJsonArray("output_wrapper_fields"), MEMBER_NAME, 32);
        Map<String, InputCountSemantics> countSemantics = parseCountSemantics(
                object.getAsJsonObject("input_count_semantics"));
        List<OutputMapping> outputMappings = parseOutputMappings(object.getAsJsonArray("output_mappings"));
        List<CountedWrapper> countedWrappers = parseCountedWrappers(object.getAsJsonArray("counted_wrappers"));
        List<DirectionRule> directions = parseDirections(object.getAsJsonArray("directions"));
        List<MultiplierRule> multipliers = parseMultipliers(object.getAsJsonArray("input_multipliers"));
        return new Profile(recipeTypes, recipeClasses, recipeClassPrefixes, includeDefaults,
                inputFields, distinctInputFields, outputFields, outputMatch,
                representationFields, structuralWrappers, outputWrappers,
                countSemantics, outputMappings, countedWrappers, directions, multipliers);
    }

    private static ResolvedProfile resolved(Object recipe)
    {
        List<Profile> matching = entries.stream().map(Entry::profile)
                .filter(profile -> matchesProfile(recipe, profile)).toList();
        boolean excludeDefaults = matching.stream().anyMatch(profile -> profile.scoped() && !profile.includeDefaults());
        LinkedHashSet<String> inputs = new LinkedHashSet<>();
        LinkedHashSet<String> distinctInputs = new LinkedHashSet<>();
        LinkedHashSet<String> outputs = new LinkedHashSet<>();
        LinkedHashSet<String> representations = new LinkedHashSet<>();
        LinkedHashSet<String> structuralWrappers = new LinkedHashSet<>();
        LinkedHashSet<String> outputWrappers = new LinkedHashSet<>();
        OutputMatchSemantics outputMatch = OutputMatchSemantics.EXACT;
        LinkedHashMap<String, InputCountSemantics> countSemantics = new LinkedHashMap<>();
        ArrayList<OutputMapping> outputMappings = new ArrayList<>();
        ArrayList<CountedWrapper> countedWrappers = new ArrayList<>();
        ArrayList<DirectionRule> directions = new ArrayList<>();
        ArrayList<MultiplierRule> multipliers = new ArrayList<>();
        for (Profile profile : matching)
        {
            if (excludeDefaults && !profile.scoped()) continue;
            inputs.addAll(profile.inputFields());
            distinctInputs.addAll(profile.distinctInputFields());
            outputs.addAll(profile.outputFields());
            if (profile.outputMatch() == OutputMatchSemantics.SAME_RESOURCE)
                outputMatch = OutputMatchSemantics.SAME_RESOURCE;
            representations.addAll(profile.representationFields());
            structuralWrappers.addAll(profile.structuralWrapperFields());
            outputWrappers.addAll(profile.outputWrapperFields());
            countSemantics.putAll(profile.inputCountSemantics());
            profile.outputMappings().forEach(value -> addDistinct(outputMappings, value));
            profile.countedWrappers().forEach(value -> addDistinct(countedWrappers, value));
            profile.directions().forEach(value -> addDistinct(directions, value));
            profile.multipliers().forEach(value -> addDistinct(multipliers, value));
        }
        return new ResolvedProfile(List.copyOf(inputs), Set.copyOf(distinctInputs), List.copyOf(outputs), outputMatch,
                List.copyOf(representations),
                List.copyOf(structuralWrappers), List.copyOf(outputWrappers), Map.copyOf(countSemantics),
                List.copyOf(outputMappings), List.copyOf(countedWrappers), List.copyOf(directions),
                List.copyOf(multipliers));
    }

    private static boolean matchesProfile(Object recipe, Profile profile)
    {
        if (!profile.recipeTypes().isEmpty())
        {
            String family = recipe instanceof Recipe<?> typed ? RecipePlanningService.family(typed.getType()) : null;
            if (family == null || !profile.recipeTypes().contains(family)) return false;
        }
        return matchesClass(recipe, profile.recipeClasses(), profile.recipeClassPrefixes());
    }

    static boolean matchesClass(Object target, Set<String> classes, Set<String> prefixes)
    {
        if (classes.isEmpty() && prefixes.isEmpty()) return true;
        if (target == null) return false;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
        {
            String name = type.getName();
            if (classes.contains(name) || prefixes.stream().anyMatch(name::startsWith)) return true;
        }
        return false;
    }

    private static Map<String, InputCountSemantics> parseCountSemantics(JsonObject values)
    {
        if (values == null) return Map.of();
        LinkedHashMap<String, InputCountSemantics> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet())
        {
            if (result.size() >= 32 || !MEMBER_NAME.matcher(entry.getKey()).matches()) continue;
            InputCountSemantics semantics = InputCountSemantics.parse(entry.getValue().getAsString());
            if (semantics != null) result.put(entry.getKey(), semantics);
        }
        return Map.copyOf(result);
    }

    private static List<OutputMapping> parseOutputMappings(JsonArray values)
    {
        if (values == null) return List.of();
        ArrayList<OutputMapping> result = new ArrayList<>();
        for (JsonElement value : values)
        {
            if (result.size() >= 32) break;
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            OutputMapping mapping = OutputMapping.parse(string(object, "type"), string(object, "id"),
                    string(object, "amount_field"));
            addDistinct(result, mapping);
        }
        return List.copyOf(result);
    }

    private static List<CountedWrapper> parseCountedWrappers(JsonArray values)
    {
        if (values == null) return List.of();
        ArrayList<CountedWrapper> result = new ArrayList<>();
        for (JsonElement value : values)
        {
            if (result.size() >= 16) break;
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            CountedWrapper wrapper = new CountedWrapper(
                    strings(object.getAsJsonArray("value_fields"), MEMBER_NAME, 16),
                    strings(object.getAsJsonArray("count_fields"), MEMBER_NAME, 16));
            if (!wrapper.valueFields().isEmpty()) addDistinct(result, wrapper);
        }
        return List.copyOf(result);
    }

    private static List<DirectionRule> parseDirections(JsonArray values)
    {
        if (values == null) return List.of();
        ArrayList<DirectionRule> result = new ArrayList<>();
        for (JsonElement value : values)
        {
            if (result.size() >= 32) break;
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            DirectionRule rule = new DirectionRule(
                    strings(object.getAsJsonArray("recipe_classes"), CLASS_NAME, 32),
                    strings(object.getAsJsonArray("recipe_class_prefixes"), CLASS_NAME, 32),
                    strings(object.getAsJsonArray("output_stack_types"), STACK_TYPE, 32),
                    strings(object.getAsJsonArray("output_stack_type_prefixes"), STACK_TYPE, 32),
                    strings(object.getAsJsonArray("output_fields"), MEMBER_NAME, 32),
                    List.copyOf(strings(object.getAsJsonArray("input_fields"), MEMBER_NAME, 32)));
            if (!rule.inputFields().isEmpty()) addDistinct(result, rule);
        }
        return List.copyOf(result);
    }

    private static List<MultiplierRule> parseMultipliers(JsonArray values)
    {
        if (values == null) return List.of();
        ArrayList<MultiplierRule> result = new ArrayList<>();
        for (JsonElement value : values)
        {
            if (result.size() >= 32) break;
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            long factor = object.has("factor") ? object.get("factor").getAsLong() : 1;
            String condition = string(object, "when_boolean_field");
            MultiplierRule rule = new MultiplierRule(
                    strings(object.getAsJsonArray("recipe_classes"), CLASS_NAME, 32),
                    strings(object.getAsJsonArray("recipe_class_prefixes"), CLASS_NAME, 32),
                    strings(object.getAsJsonArray("input_fields"), MEMBER_NAME, 32),
                    valid(condition, MEMBER_NAME), Math.max(1, factor));
            if (!rule.inputFields().isEmpty()) addDistinct(result, rule);
        }
        return List.copyOf(result);
    }

    private static Set<String> strings(JsonArray values, Pattern pattern, int limit)
    {
        if (values == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonElement value : values)
        {
            if (result.size() >= limit) break;
            String parsed = valid(value.getAsString(), pattern);
            if (!parsed.isBlank()) result.add(parsed);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> with(Set<String> values, String value)
    {
        if (value.isBlank()) return values;
        LinkedHashSet<String> result = new LinkedHashSet<>(values);
        result.add(value);
        return Collections.unmodifiableSet(result);
    }

    private static String valid(String value, Pattern pattern)
    { return value != null && pattern.matcher(value).matches() ? value : ""; }

    private static String string(JsonObject object, String name)
    { return object.has(name) && object.get(name).isJsonPrimitive() ? object.get(name).getAsString() : ""; }

    private static <T> void addDistinct(List<T> values, T value)
    { if (value != null && !values.contains(value)) values.add(value); }

    private static final class Logging
    {
        private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RecipeIoProfileRegistry.class);
    }

    private record Entry(Profile profile, String encoded) {}

    public enum InputCountSemantics
    {
        REQUIRED("required"), BATCH_LIMIT("batch_limit");

        private final String encoded;
        InputCountSemantics(String encoded) { this.encoded = encoded; }
        static InputCountSemantics parse(String value)
        {
            for (InputCountSemantics semantics : values()) if (semantics.encoded.equals(value)) return semantics;
            return null;
        }
    }

    public enum OutputType
    {
        ITEM("item"), FLUID("fluid");

        private final String encoded;
        OutputType(String encoded) { this.encoded = encoded; }
        static OutputType parse(String value)
        {
            for (OutputType type : values()) if (type.encoded.equals(value)) return type;
            return null;
        }
    }

    public enum OutputMatchSemantics
    {
        EXACT("exact"), SAME_RESOURCE("same_resource");

        private final String encoded;
        OutputMatchSemantics(String encoded) { this.encoded = encoded; }
        static OutputMatchSemantics parse(String value)
        {
            for (OutputMatchSemantics semantics : values())
                if (semantics.encoded.equals(value)) return semantics;
            return EXACT;
        }
    }

    public record OutputMapping(OutputType type, String id, String amountField)
    {
        static OutputMapping parse(String type, String id, String amountField)
        {
            OutputType parsedType = OutputType.parse(type);
            if (parsedType == null || !RESOURCE_ID.matcher(id).matches()
                    || !MEMBER_NAME.matcher(amountField).matches()) return null;
            return new OutputMapping(parsedType, id, amountField);
        }
    }

    public record CountedWrapper(Set<String> valueFields, Set<String> countFields) {}

    public record DirectionRule(Set<String> recipeClasses, Set<String> recipeClassPrefixes,
                                Set<String> outputStackTypes, Set<String> outputStackTypePrefixes,
                                Set<String> outputFields, List<String> inputFields)
    {
        boolean matchesClass(Object recipe)
        { return RecipeIoProfileRegistry.matchesClass(recipe, recipeClasses, recipeClassPrefixes); }

        boolean matchesStackType(String id, String path)
        {
            if (outputStackTypes.isEmpty() && outputStackTypePrefixes.isEmpty())
                return outputFields.isEmpty();
            if (outputStackTypes.contains(id) || outputStackTypes.contains(path)) return true;
            return outputStackTypePrefixes.stream().anyMatch(prefix -> id.startsWith(prefix)
                    || path.startsWith(prefix));
        }
    }

    public record MultiplierRule(Set<String> recipeClasses, Set<String> recipeClassPrefixes,
                                 Set<String> inputFields, String whenBooleanField, long factor) {}

    public record Profile(Set<String> recipeTypes, Set<String> recipeClasses,
                          Set<String> recipeClassPrefixes, boolean includeDefaults,
                          Set<String> inputFields, Set<String> distinctInputFields, Set<String> outputFields,
                          OutputMatchSemantics outputMatch,
                          Set<String> representationFields, Set<String> structuralWrapperFields,
                          Set<String> outputWrapperFields,
                          Map<String, InputCountSemantics> inputCountSemantics,
                          List<OutputMapping> outputMappings, List<CountedWrapper> countedWrappers,
                          List<DirectionRule> directions, List<MultiplierRule> multipliers)
    {
        boolean scoped()
        { return !recipeTypes.isEmpty() || !recipeClasses.isEmpty() || !recipeClassPrefixes.isEmpty(); }
    }

    private record ResolvedProfile(List<String> inputFields, Set<String> distinctInputFields,
                                   List<String> outputFields, OutputMatchSemantics outputMatch,
                                   List<String> representationFields, List<String> structuralWrapperFields,
                                   List<String> outputWrapperFields,
                                   Map<String, InputCountSemantics> inputCountSemantics,
                                   List<OutputMapping> outputMappings, List<CountedWrapper> countedWrappers,
                                   List<DirectionRule> directions, List<MultiplierRule> multipliers) {}
}
