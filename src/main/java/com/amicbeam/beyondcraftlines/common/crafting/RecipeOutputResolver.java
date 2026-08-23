package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Resolves item and third-party resource outputs without linking against their owning mods. */
public final class RecipeOutputResolver
{
    private static final List<String> OUTPUT_METHODS = List.of(
            "getOutputDefinition", "getOutputDefinitions",
            "getGasOutputDefinition", "getChemicalOutputDefinition", "getFluidOutputDefinition",
            "outputs", "getOutputs",
            // Ars Nouveau 1.20 and similar data-driven recipes expose a public result/output
            // field while intentionally returning ItemStack.EMPTY from Recipe#getResultItem.
            "output", "getOutput", "result", "getResult");

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
            if (converted == null)
                for (Object representation : CountedInputReflection.representationValues(output))
                {
                    KeyAmount represented = RecipeResourceResolver.fromStack(representation);
                    if (represented != null && !represented.isEmpty()) add(result, represented);
                }
        }
        return List.copyOf(result.values());
    }

    static List<Object> reflectiveOutputValues(Object recipe)
    { return reflectiveOutputValues(recipe, OUTPUT_METHODS); }

    static List<Object> reflectiveOutputValues(Object recipe, List<String> methods)
    {
        List<Object> result = new ArrayList<>();
        for (String method : methods)
            result.addAll(flatten(RecipeReflection.readPublicMember(recipe, method)));
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
        List<Object> result = new ArrayList<>();
        for (Object leaf : StructuralRecipeValues.flatten(value))
        {
            Object current = leaf;
            java.util.Set<Object> wrappers = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<>());
            for (int depth = 0; depth < 8 && current != null && wrappers.add(current); depth++)
            {
                Object containedStack = RecipeReflection.readPublicMember(current, "getChemicalStack");
                if (containedStack == null || containedStack == current) break;
                current = containedStack;
            }
            if (current != null) result.addAll(StructuralRecipeValues.flatten(current));
        }
        return List.copyOf(result);
    }
}
