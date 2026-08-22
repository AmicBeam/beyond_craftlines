package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
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

    static final List<String> INPUT_METHODS = List.of(
            "input", "getInput", "inputs", "getInputs",
            "inputA", "getInputA", "inputB", "getInputB", "inputC", "getInputC",
            "inputOne", "getInputOne", "inputTwo", "getInputTwo", "inputThree", "getInputThree",
            "leftInput", "getLeftInput", "rightInput", "getRightInput",
            "middleInput", "getMiddleInput",
            "primaryInput", "getPrimaryInput", "primaryInputs", "getPrimaryInputs",
            "main", "getMain", "mainInput", "getMainInput",
            "activationItem", "getActivationItem",
            "extraInput", "getExtraInput", "extraInputs", "getExtraInputs",
            "secondaryInput", "getSecondaryInput", "secondaryInputs", "getSecondaryInputs",
            "itemInput", "getItemInput", "itemInputs", "getItemInputs",
            "inputItem", "getInputItem", "inputItems", "getInputItems",
            "solidInput", "getSolidInput", "inputSolid", "getInputSolid",
            "fluidInput", "getFluidInput", "fluidInputs", "getFluidInputs",
            "inputFluid", "getInputFluid", "inputFluids", "getInputFluids",
            "fluidIngredient", "getFluidIngredient", "fluidIngredients", "getFluidIngredients",
            "chemicalInput", "getChemicalInput", "chemicalInputs", "getChemicalInputs",
            "inputChemical", "getInputChemical", "inputChemicals", "getInputChemicals",
            "chemicalIngredient", "getChemicalIngredient", "chemicalIngredients", "getChemicalIngredients",
            "gasInput", "getGasInput", "gasInputs", "getGasInputs",
            "inputGas", "getInputGas", "inputGases", "getInputGases",
            "gasIngredient", "getGasIngredient", "gasIngredients", "getGasIngredients",
            "offerings", "getOfferings");

    private CountedInputReflection() {}

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
