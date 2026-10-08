package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/** Structural reflection for third-party counted item inputs, kept free of mod-specific classes. */
final class CountedInputReflection
{
    private CountedInputReflection() {}

    /** Stable, protocol-safe name for the logical input section exposed by an accessor. */
    static String inputGroup(String methodName)
    {
        String value = methodName.startsWith("get") && methodName.length() > 3
                ? Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4) : methodName;
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    static java.util.List<?> flatten(Object value)
    { return flatten(null, value); }

    static java.util.List<?> flatten(Object recipe, Object value)
    { return StructuralRecipeValues.flatten(value, recipe); }

    static Value read(Object input)
    { return read(null, input); }

    static Value read(Object recipe, Object input)
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
            if (hasRepresentationMethod(recipe, current)) break;
            Object ingredient = null;
            Object rawCount = null;
            for (RecipeIoProfileRegistry.CountedWrapper wrapper :
                    RecipeIoProfileRegistry.countedWrappers(recipe))
            {
                ingredient = first(current, wrapper.valueFields());
                if (ingredient == null) continue;
                rawCount = first(current, wrapper.countFields());
                break;
            }
            if (ingredient == null) break;
            wrapped = true;
            long factor = rawCount instanceof Number number ? Math.max(1L, number.longValue()) : 1L;
            count = saturatedMultiply(count, factor);
            current = ingredient;
        }
        return wrapped && current != null ? new Value(current, count) : null;
    }

    static java.util.List<?> representationValues(Object ingredient)
    { return representationValues(null, ingredient); }

    static java.util.List<?> representationValues(Object recipe, Object ingredient)
    {
        if (ingredient == null) return java.util.List.of();
        for (String method : RecipeIoProfileRegistry.representationMembers(recipe))
        {
            Object values = invokeNoArgs(ingredient, method);
            java.util.List<?> flattened = flatten(recipe, values);
            if (!flattened.isEmpty()) return flattened;
        }
        return java.util.List.of();
    }

    static boolean hasRepresentationMethod(Object target)
    { return hasRepresentationMethod(null, target); }

    static boolean hasRepresentationMethod(Object recipe, Object target)
    { return RecipeIoProfileRegistry.representationMembers(recipe).stream()
            .anyMatch(method -> hasNoArgMethod(target, method)); }

    static long recipeInputMultiplier(Object recipe, String inputMethod)
    { return RecipeIoProfileRegistry.inputMultiplier(recipe, inputMethod); }

    private static Object first(Object target, Set<String> members)
    {
        for (String member : members)
        {
            Object value = invokeNoArgs(target, member);
            if (value != null) return value;
        }
        return null;
    }

    private static long saturatedMultiply(long left, long right)
    {
        if (left == 0 || right == 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static boolean hasNoArgMethod(Object target, String name)
    {
        try { return target.getClass().getMethod(name).getParameterCount() == 0; }
        catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static Object invokeNoArgs(Object target, String name)
    {
        try
        {
            Method method = target.getClass().getMethod(name);
            if (method.getParameterCount() != 0) return null;
            if (!method.canAccess(target) && !method.trySetAccessible()) return null;
            return method.invoke(target);
        }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    record Value(Object ingredient, long count) {}
}
