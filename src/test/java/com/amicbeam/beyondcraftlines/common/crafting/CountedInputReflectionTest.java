package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CountedInputReflectionTest
{
    @Test
    void readsRecordStyleIngredientAndCount()
    {
        Object ingredient = new Object();
        var value = CountedInputReflection.read(new RecordStyleInput(ingredient, 4));
        assertEquals(ingredient, value.ingredient());
        assertEquals(4, value.count());
    }

    @Test
    void readsBeanStyleAndClampsInvalidCount()
    {
        Object ingredient = new Object();
        var value = CountedInputReflection.read(new BeanStyleInput(ingredient, 0));
        assertEquals(ingredient, value.ingredient());
        assertEquals(1, value.count());
    }

    @Test
    void flattensCollectionsAndArraysButKeepsEqualEntries()
    {
        Object repeated = new Object();
        assertEquals(List.of(repeated, repeated),
                CountedInputReflection.flatten(new Object[] { repeated, repeated }));
        assertEquals(List.of(repeated, repeated),
                CountedInputReflection.flatten(List.of(repeated, repeated)));
    }

    @Test
    void recursivelyFlattensNestedContainersAndOptionals()
    {
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();
        assertEquals(List.of(first, second, third), CountedInputReflection.flatten(
                List.of(Optional.of(first), new Object[] { List.of(second), third })));
        assertEquals(List.of(), CountedInputReflection.flatten(Optional.empty()));
    }

    @Test
    void safelyStopsOnSelfReferentialContainers()
    {
        List<Object> cyclic = new java.util.ArrayList<>();
        cyclic.add(cyclic);
        assertEquals(List.of(), CountedInputReflection.flatten(cyclic));
    }

    @Test
    void unwrapsNestedCountedIngredientsAndSaturatesCount()
    {
        Object ingredient = new Object();
        var nested = new RecordStyleInput(new RecordStyleInput(ingredient, 4), 3);
        var value = CountedInputReflection.read(nested);
        assertEquals(ingredient, value.ingredient());
        assertEquals(12, value.count());

        var saturated = CountedInputReflection.read(new LongCountInput(
                new LongCountInput(ingredient, Long.MAX_VALUE), 2));
        assertEquals(Long.MAX_VALUE, saturated.count());
    }

    @Test
    void inputDiscoveryDoesNotProbeEnergyMetadata()
    {
        assertFalse(CountedInputReflection.INPUT_METHODS.stream()
                .anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("energy")));
    }

    @Test
    void rejectsObjectsWithoutAnIngredientAccessor()
    {
        assertNull(CountedInputReflection.read(new Object()));
    }

    private record RecordStyleInput(Object ingredient, int count) {}
    private record LongCountInput(Object ingredient, long count) {}

    private static final class BeanStyleInput
    {
        private final Object ingredient;
        private final int count;
        private BeanStyleInput(Object ingredient, int count)
        {
            this.ingredient = ingredient;
            this.count = count;
        }
        public Object getIngredient() { return ingredient; }
        public int getCount() { return count; }
    }
}
