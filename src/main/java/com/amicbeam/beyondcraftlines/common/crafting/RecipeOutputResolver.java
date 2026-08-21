package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Resolves item and third-party resource outputs without linking against their owning mods. */
public final class RecipeOutputResolver
{
    private static final List<String> OUTPUT_METHODS = List.of(
            "getOutputDefinition", "getOutputDefinitions",
            "getGasOutputDefinition", "getChemicalOutputDefinition", "getFluidOutputDefinition",
            "outputs", "getOutputs");

    private RecipeOutputResolver() {}

    public static List<KeyAmount> outputs(Recipe<?> recipe,
                                          net.minecraft.core.HolderLookup.Provider registries)
    {
        LinkedHashMap<IStackKey<?>, KeyAmount> result = new LinkedHashMap<>();
        ItemStack item = recipe.getResultItem(registries);
        if (!item.isEmpty()) add(result, new KeyAmount(new ItemStackKey(item.copyWithCount(1)), item.getCount()));
        for (Object output : reflectiveOutputValues(recipe))
        {
            KeyAmount converted = RecipeResourceResolver.fromStack(output);
            if (converted != null && !converted.isEmpty()) add(result, converted);
        }
        return List.copyOf(result.values());
    }

    static List<Object> reflectiveOutputValues(Object recipe)
    {
        List<Object> result = new ArrayList<>();
        for (String method : OUTPUT_METHODS) result.addAll(flatten(invokeNoArgs(recipe, method)));
        return List.copyOf(result);
    }

    public static KeyAmount primary(Recipe<?> recipe, net.minecraft.core.HolderLookup.Provider registries)
    {
        List<KeyAmount> outputs = outputs(recipe, registries);
        return outputs.isEmpty() ? null : outputs.getFirst();
    }

    private static void add(LinkedHashMap<IStackKey<?>, KeyAmount> result, KeyAmount value)
    { result.putIfAbsent(value.key(), value); }

    private static List<?> flatten(Object value)
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
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) result.add(java.lang.reflect.Array.get(value, i));
            return result;
        }
        return List.of(value);
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
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
}
