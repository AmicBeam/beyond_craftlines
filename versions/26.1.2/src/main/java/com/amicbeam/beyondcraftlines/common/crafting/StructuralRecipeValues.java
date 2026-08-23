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
 * <p>Only values explicitly returned by the resolver's whitelisted recipe accessors reach this
 * class. Map keys are metadata such as recipe capabilities and are deliberately ignored.</p>
 */
final class StructuralRecipeValues
{
    private static final int MAX_DEPTH = 32;
    private static final int MAX_VISITS = 4_096;

    private StructuralRecipeValues() {}

    static List<?> flatten(Object value)
    {
        List<Object> result = new ArrayList<>();
        flatten(value, result, Collections.newSetFromMap(new IdentityHashMap<>()), new Budget(), 0);
        return List.copyOf(result);
    }

    private static void flatten(Object value, List<Object> result, Set<Object> containers,
                                Budget budget, int depth)
    {
        if (value == null || depth > MAX_DEPTH || !budget.visit()) return;
        if (value instanceof Optional<?> optional)
        {
            optional.ifPresent(element -> flatten(element, result, containers, budget, depth + 1));
            return;
        }
        if (value instanceof Map<?, ?> map)
        {
            if (!containers.add(value)) return;
            for (Object element : map.values())
            {
                if (budget.exhausted()) break;
                flatten(element, result, containers, budget, depth + 1);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable)
        {
            if (!containers.add(value)) return;
            for (Object element : iterable)
            {
                if (budget.exhausted()) break;
                flatten(element, result, containers, budget, depth + 1);
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
                    flatten(iterator.next(), result, containers, budget, depth + 1);
            }
            finally { stream.close(); }
            return;
        }
        if (value.getClass().isArray())
        {
            if (!containers.add(value)) return;
            int length = Array.getLength(value);
            for (int i = 0; i < length && !budget.exhausted(); i++)
                flatten(Array.get(value, i), result, containers, budget, depth + 1);
            return;
        }
        if (value.getClass().isRecord())
        {
            if (!containers.add(value)) return;
            RecordComponent[] components = value.getClass().getRecordComponents();
            for (RecordComponent component : components)
                if ("content".equals(component.getName()))
                {
                    flatten(RecipeReflection.readPublicMember(value, component.getName()), result,
                            containers, budget, depth + 1);
                    return;
                }
            for (RecordComponent component : components)
            {
                if (budget.exhausted()) break;
                flatten(RecipeReflection.readPublicMember(value, component.getName()), result,
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
