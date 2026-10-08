package com.amicbeam.beyondcraftlines.common.crafting;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded structural traversal for public recipe I/O containers.
 *
 * <p>Only values explicitly returned by the resolver's datapack-profiled recipe accessors reach this
 * class. Map keys are metadata such as recipe capabilities and are deliberately ignored.</p>
 */
final class StructuralRecipeValues
{
    private static final int MAX_DEPTH = 32;
    private static final int MAX_VISITS = 4_096;

    private StructuralRecipeValues() {}

    static List<?> flatten(Object value)
    { return flatten(value, null); }

    static List<?> flatten(Object value, Object recipe)
    {
        List<Object> result = new ArrayList<>();
        flatten(value, recipe, result, Collections.newSetFromMap(new IdentityHashMap<>()), new Budget(), 0);
        return List.copyOf(result);
    }

    private static void flatten(Object value, Object recipe, List<Object> result, Set<Object> containers,
                                Budget budget, int depth)
    {
        if (value == null || depth > MAX_DEPTH || !budget.visit()) return;
        if (value instanceof Optional<?> optional)
        {
            optional.ifPresent(element -> flatten(element, recipe, result, containers, budget, depth + 1));
            return;
        }
        if (value instanceof Map<?, ?> map)
        {
            if (!containers.add(value)) return;
            for (Object element : map.values())
            {
                if (budget.exhausted()) break;
                flatten(element, recipe, result, containers, budget, depth + 1);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable)
        {
            if (!containers.add(value)) return;
            for (Object element : iterable)
            {
                if (budget.exhausted()) break;
                flatten(element, recipe, result, containers, budget, depth + 1);
            }
            return;
        }
        if (value instanceof java.util.stream.BaseStream<?, ?> stream)
        {
            if (!containers.add(value)) return;
            try
            {
                var iterator = stream.iterator();
                while (!budget.exhausted() && iterator.hasNext())
                    flatten(iterator.next(), recipe, result, containers, budget, depth + 1);
            }
            finally { stream.close(); }
            return;
        }
        if (value.getClass().isArray())
        {
            if (!containers.add(value)) return;
            int length = Array.getLength(value);
            for (int i = 0; i < length && !budget.exhausted(); i++)
                flatten(Array.get(value, i), recipe, result, containers, budget, depth + 1);
            return;
        }
        // A representation provider is already a semantic recipe-input leaf. In particular,
        // Malum 1.21's SpiritIngredient is a record whose getItems() exposes the actual spirit
        // shard stack. Expanding that record into its holder and numeric count first discards
        // the only representation that Beyond Dimensions can convert into a resource key.
        if (CountedInputReflection.hasRepresentationMethod(recipe, value))
        {
            result.add(value);
            return;
        }
        // Capability recipe APIs commonly wrap the actual stack/ingredient in a Content
        // object alongside chance metadata. Depending on the mod version this wrapper may
        // be a record, a normal class with a public field, or a bean with getContent().
        // Unwrap the semantic payload before considering generic record components so chance,
        // maxChance and similar numbers can never become recipe resources.
        Object content = null;
        for (String member : RecipeIoProfileRegistry.structuralWrapperMembers(recipe))
        {
            content = RecipeReflection.readPublicMember(value, member);
            if (content != null) break;
        }
        if (content != null && content != value)
        {
            if (!containers.add(value)) return;
            flatten(content, recipe, result, containers, budget, depth + 1);
            return;
        }
        if (value.getClass().isRecord())
        {
            if (!containers.add(value)) return;
            RecordComponent[] components = value.getClass().getRecordComponents();
            for (RecordComponent component : components)
            {
                if (budget.exhausted()) break;
                flatten(RecipeReflection.readPublicMember(value, component.getName()), recipe, result,
                        containers, budget, depth + 1);
            }
            return;
        }
        result.add(value);
    }

    private static final class Budget
    {
        private int visits;
        private boolean visit() { return visits++ < MAX_VISITS; }
        private boolean exhausted() { return visits >= MAX_VISITS; }
    }
}
