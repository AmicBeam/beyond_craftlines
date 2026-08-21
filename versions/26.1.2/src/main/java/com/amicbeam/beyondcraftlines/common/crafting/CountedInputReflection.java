package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Structural reflection for third-party counted item inputs, kept free of mod-specific classes. */
final class CountedInputReflection
{
    static final List<String> INPUT_METHODS = List.of(
            "input", "getInput", "inputs", "getInputs",
            "inputA", "getInputA", "inputB", "getInputB", "inputC", "getInputC",
            "inputOne", "getInputOne", "inputTwo", "getInputTwo", "inputThree", "getInputThree",
            "leftInput", "getLeftInput", "rightInput", "getRightInput",
            "middleInput", "getMiddleInput",
            "primaryInput", "getPrimaryInput", "primaryInputs", "getPrimaryInputs",
            "main", "getMain", "mainInput", "getMainInput",
            "extraInput", "getExtraInput", "extraInputs", "getExtraInputs",
            "secondaryInput", "getSecondaryInput", "secondaryInputs", "getSecondaryInputs",
            "itemInput", "getItemInput", "itemInputs", "getItemInputs",
            "inputItem", "getInputItem", "inputItems", "getInputItems",
            "solidInput", "getSolidInput", "inputSolid", "getInputSolid",
            "fluidInput", "getFluidInput", "fluidInputs", "getFluidInputs",
            "inputFluid", "getInputFluid", "inputFluids", "getInputFluids",
            "chemicalInput", "getChemicalInput", "chemicalInputs", "getChemicalInputs",
            "inputChemical", "getInputChemical", "inputChemicals", "getInputChemicals",
            "gasInput", "getGasInput", "gasInputs", "getGasInputs",
            "inputGas", "getInputGas", "inputGases", "getInputGases",
            "offerings", "getOfferings");

    private CountedInputReflection() {}

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
            if (hasNoArgMethod(current, "getRepresentations")) break;
            Object ingredient = invokeNoArgs(current, "ingredient");
            if (ingredient == null) ingredient = invokeNoArgs(current, "getIngredient");
            if (ingredient == null) break;
            wrapped = true;
            Object rawCount = invokeNoArgs(current, "count");
            if (!(rawCount instanceof Number)) rawCount = invokeNoArgs(current, "getCount");
            long factor = rawCount instanceof Number number ? Math.max(1L, number.longValue()) : 1L;
            count = saturatedMultiply(count, factor);
            current = ingredient;
        }
        return wrapped && current != null ? new Value(current, count) : null;
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
