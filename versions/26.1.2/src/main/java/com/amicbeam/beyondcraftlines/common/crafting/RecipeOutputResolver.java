package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

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
            // Malum exposes Spirit Focusing results through these accessors.
            "getOutputRaw", "createOutput",
            "output", "getOutput", "result", "getResult");

    private RecipeOutputResolver() {}

    public static List<KeyAmount> outputs(Recipe<?> recipe, Level level)
    {
        LinkedHashMap<IStackKey<?>, KeyAmount> result = new LinkedHashMap<>();
        var context = SlotDisplayContext.fromLevel(level);
        recipe.display().stream().flatMap(display -> display.result().resolveForStacks(context).stream())
                .filter(item -> !item.isEmpty())
                .forEach(item -> add(result,
                        new KeyAmount(new ItemStackKey(item.copyWithCount(1)), item.getCount())));
        for (Object output : reflectiveOutputValues(recipe,
                RecipeFieldWhitelistRegistry.outputMembers(recipe, OUTPUT_METHODS)))
            addOutput(result, output);
        return List.copyOf(result.values());
    }

    private static void addOutput(LinkedHashMap<IStackKey<?>, KeyAmount> result, Object output)
    {
        if (output instanceof Ingredient ingredient)
        {
            ingredient.items().map(ItemStack::new).filter(stack -> !stack.isEmpty())
                    .forEach(stack -> add(result, new KeyAmount(
                            new ItemStackKey(stack.copyWithCount(1)), Math.max(1, stack.getCount()))));
            return;
        }
        KeyAmount converted = RecipeResourceResolver.fromStack(output);
        if (converted != null && !converted.isEmpty())
        {
            add(result, converted);
            return;
        }
        for (Object representation : CountedInputReflection.representationValues(output))
        {
            KeyAmount represented = RecipeResourceResolver.fromStack(representation);
            if (represented != null && !represented.isEmpty()) add(result, represented);
        }
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

    public static KeyAmount primary(Recipe<?> recipe, Level level)
    {
        List<KeyAmount> outputs = outputs(recipe, level);
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
