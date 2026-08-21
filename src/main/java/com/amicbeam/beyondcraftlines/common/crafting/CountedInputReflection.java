package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Structural reflection for third-party counted item inputs, kept free of mod-specific classes. */
final class CountedInputReflection
{
    static final List<String> INPUT_METHODS = List.of(
            "input", "getInput", "inputs", "getInputs",
            "main", "getMain", "mainInput", "getMainInput",
            "extraInput", "getExtraInput", "extraInputs", "getExtraInputs",
            "secondaryInput", "getSecondaryInput", "secondaryInputs", "getSecondaryInputs",
            "offerings", "getOfferings");

    private CountedInputReflection() {}

    static List<?> flatten(Object value)
    {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list;
        if (value instanceof Iterable<?> iterable)
        {
            List<Object> result = new ArrayList<>();
            iterable.forEach(result::add);
            return result;
        }
        if (value.getClass().isArray())
        {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) result.add(Array.get(value, i));
            return result;
        }
        return List.of(value);
    }

    static Value read(Object input)
    {
        if (input == null) return null;
        Object ingredient = invokeNoArgs(input, "ingredient");
        if (ingredient == null) ingredient = invokeNoArgs(input, "getIngredient");
        if (ingredient == null) return null;
        Object rawCount = invokeNoArgs(input, "count");
        if (!(rawCount instanceof Number)) rawCount = invokeNoArgs(input, "getCount");
        long count = rawCount instanceof Number number ? Math.max(1L, number.longValue()) : 1L;
        return new Value(ingredient, count);
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
