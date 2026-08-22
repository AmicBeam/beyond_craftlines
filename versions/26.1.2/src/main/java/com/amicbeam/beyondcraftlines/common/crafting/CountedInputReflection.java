package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Structural reflection for third-party counted item inputs, kept free of mod-specific classes. */
final class CountedInputReflection
{
    private static final List<String> REPRESENTATION_METHODS = List.of(
            "getRepresentations", "representations",
            "getMatchingFluidStacks", "matchingFluidStacks",
            "getMatchingStacks", "matchingStacks",
            "getFluids", "fluids", "getFluidStacks", "fluidStacks",
            "getStacks", "stacks", "getInputStacks", "inputStacks");
    private static final long MEKANISM_PER_TICK_CHEMICAL_MULTIPLIER = 200;
    private static final Set<String> CHEMICAL_INPUT_METHODS = Set.of(
            "chemicalInput", "getChemicalInput", "chemicalInputs", "getChemicalInputs",
            "inputChemical", "getInputChemical", "inputChemicals", "getInputChemicals");
    private static final Set<String> MERGED_OR_OUTPUT_GROUPS = Set.of(
            "ingredient", "ingredients", "result", "results", "result_item", "result_items",
            "output", "outputs", "output_item", "output_items", "remaining_items");

    private CountedInputReflection() {}

    /**
     * Finds logical input sections by their public no-argument accessors. Discovery is based on
     * returned value structure, never on a mod id, recipe id, or third-party class name.
     */
    static List<InputSection> inputSections(Object target)
    {
        if (target == null) return List.of();
        Map<String, InputSection> sections = new LinkedHashMap<>();
        Arrays.stream(target.getClass().getMethods())
                .filter(CountedInputReflection::mayExposeInputSection)
                .sorted(Comparator.comparing(Method::getName))
                .forEach(method ->
                {
                    String group = inputGroup(method.getName());
                    if (sections.containsKey(group)) return; // getFoo()/foo() aliases
                    List<?> values = flatten(invokeNoArgs(target, method));
                    if (values.isEmpty() || values.stream().anyMatch(value -> !isInputValue(value))) return;
                    sections.put(group, new InputSection(method.getName(), group, values));
                });
        return List.copyOf(sections.values());
    }

    private static boolean mayExposeInputSection(Method method)
    {
        if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())
                || method.isBridge() || method.isSynthetic() || method.getDeclaringClass() == Object.class)
            return false;
        String group = inputGroup(method.getName());
        if (MERGED_OR_OUTPUT_GROUPS.contains(group)
                || group.contains("output") || group.contains("result")) return false;
        Class<?> type = method.getReturnType();
        if (type == void.class || type.isPrimitive() || type.isEnum()
                || Number.class.isAssignableFrom(type) || CharSequence.class.isAssignableFrom(type))
            return false;
        return hasTypeNamed(type, "net.minecraft.world.item.crafting.Ingredient") || type.isArray()
                || Iterable.class.isAssignableFrom(type) || Optional.class.isAssignableFrom(type)
                || java.util.stream.BaseStream.class.isAssignableFrom(type)
                || hasNoArgMethod(type, "ingredient") || hasNoArgMethod(type, "getIngredient")
                || REPRESENTATION_METHODS.stream().anyMatch(name -> hasNoArgMethod(type, name));
    }

    private static boolean isInputValue(Object value)
    {
        if (hasTypeNamed(value.getClass(), "net.minecraft.world.item.crafting.Ingredient")) return true;
        Value wrapped = read(value);
        Object source = wrapped == null ? value : wrapped.ingredient();
        return source != null && (hasTypeNamed(source.getClass(),
                "net.minecraft.world.item.crafting.Ingredient")
                || !representationValues(source).isEmpty());
    }

    /** Matches repeated values by identity and occurrence instead of collapsing equal inputs. */
    static int[] matchIdentityOccurrences(List<?> available, List<?> requested)
    {
        int[] matches = new int[requested.size()];
        Arrays.fill(matches, -1);
        boolean[] used = new boolean[available.size()];
        for (int request = 0; request < requested.size(); request++)
        {
            Object wanted = requested.get(request);
            for (int candidate = 0; candidate < available.size(); candidate++)
                if (!used[candidate] && available.get(candidate) == wanted)
                {
                    used[candidate] = true;
                    matches[request] = candidate;
                    break;
                }
        }
        return matches;
    }

    /** Stable, protocol-safe name for the logical input section exposed by an accessor. */
    static String inputGroup(String methodName)
    {
        String value = methodName.startsWith("get") && methodName.length() > 3
                ? Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4) : methodName;
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    static List<?> flatten(Object value)
    {
        List<Object> result = new ArrayList<>();
        flatten(value, result, Collections.newSetFromMap(new IdentityHashMap<>()));
        return result;
    }

    private static void flatten(Object value, List<Object> result, Set<Object> containers)
    {
        if (value == null) return;
        if (value instanceof Optional<?> optional)
        {
            optional.ifPresent(element -> flatten(element, result, containers));
            return;
        }
        if (value instanceof Iterable<?> iterable)
        {
            if (!containers.add(value)) return;
            iterable.forEach(element -> flatten(element, result, containers));
            return;
        }
        if (value instanceof java.util.stream.BaseStream<?, ?> stream)
        {
            if (!containers.add(value)) return;
            try { stream.iterator().forEachRemaining(element -> flatten(element, result, containers)); }
            finally { stream.close(); }
            return;
        }
        if (value.getClass().isArray())
        {
            if (!containers.add(value)) return;
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) flatten(Array.get(value, i), result, containers);
            return;
        }
        result.add(value);
    }

    static Value read(Object input)
    {
        if (input == null) return null;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = input;
        long count = 1;
        boolean wrapped = false;
        for (int depth = 0; depth < 8 && current != null && seen.add(current); depth++)
        {
            // InputIngredient implementations (notably chemical/fluid ingredients) may
            // themselves look like counted wrappers while their public representations
            // carry the actual resource type and amount. Keep those objects intact.
            if (hasRepresentationMethod(current)) break;
            Object ingredient = invokeNoArgs(current, "ingredient");
            if (ingredient == null) ingredient = invokeNoArgs(current, "getIngredient");
            if (ingredient == null) break;
            wrapped = true;
            Object rawCount = invokeNoArgs(current, "count");
            if (!(rawCount instanceof Number)) rawCount = invokeNoArgs(current, "getCount");
            if (!(rawCount instanceof Number)) rawCount = invokeNoArgs(current, "amount");
            if (!(rawCount instanceof Number)) rawCount = invokeNoArgs(current, "getAmount");
            long factor = rawCount instanceof Number number ? Math.max(1L, number.longValue()) : 1L;
            count = saturatedMultiply(count, factor);
            current = ingredient;
        }
        return wrapped && current != null ? new Value(current, count) : null;
    }

    static List<?> representationValues(Object ingredient)
    {
        if (ingredient == null) return List.of();
        for (String method : REPRESENTATION_METHODS)
        {
            Object values = invokeNoArgs(ingredient, method);
            List<?> flattened = flatten(values);
            if (!flattened.isEmpty()) return flattened;
        }
        return List.of();
    }

    private static boolean hasRepresentationMethod(Object target)
    { return REPRESENTATION_METHODS.stream().anyMatch(method -> hasNoArgMethod(target, method)); }

    /** Mirrors Mekanism's recipe-viewer total for chemicals consumed once per processing tick. */
    static long recipeInputMultiplier(Object recipe, String inputMethod)
    {
        if (recipe == null || !CHEMICAL_INPUT_METHODS.contains(inputMethod)
                || !recipe.getClass().getName().startsWith("mekanism.")) return 1;
        Object perTickUsage = invokeNoArgs(recipe, "perTickUsage");
        if (perTickUsage instanceof Boolean enabled)
            return enabled ? MEKANISM_PER_TICK_CHEMICAL_MULTIPLIER : 1;
        // Mekanism 1.20.x predates the perTickUsage flag. Its JEI category applies
        // the same 200-tick total to every ItemStackGasToItemStackRecipe.
        return hasTypeNamed(recipe.getClass(), "mekanism.api.recipes.ItemStackGasToItemStackRecipe")
                ? MEKANISM_PER_TICK_CHEMICAL_MULTIPLIER : 1;
    }

    private static boolean hasTypeNamed(Class<?> type, String expectedName)
    {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            if (current.getName().equals(expectedName)) return true;
        return false;
    }

    private static long saturatedMultiply(long left, long right)
    {
        if (left == 0 || right == 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static boolean hasNoArgMethod(Object target, String name)
    { return target != null && hasNoArgMethod(target.getClass(), name); }

    private static boolean hasNoArgMethod(Class<?> type, String name)
    {
        try { return type.getMethod(name).getParameterCount() == 0; }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return false; }
    }

    private static Object invokeNoArgs(Object target, String name)
    {
        try
        {
            Method method = target.getClass().getMethod(name);
            return invokeNoArgs(target, method);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }

    private static Object invokeNoArgs(Object target, Method method)
    {
        try
        {
            if (method.getParameterCount() != 0) return null;
            if (!method.canAccess(target) && !method.trySetAccessible()) return null;
            return method.invoke(target);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }

    record InputSection(String methodName, String inputGroup, List<?> inputs)
    {
        InputSection
        {
            if (methodName == null || methodName.isBlank() || inputGroup == null
                    || inputGroup.isBlank() || inputs == null || inputs.isEmpty())
                throw new IllegalArgumentException("invalid input section");
            inputs = List.copyOf(inputs);
        }
    }
    record Value(Object ingredient, long count) {}
}
