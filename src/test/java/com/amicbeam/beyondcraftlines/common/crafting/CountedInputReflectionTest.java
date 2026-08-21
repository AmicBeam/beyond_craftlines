package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void rejectsObjectsWithoutAnIngredientAccessor()
    {
        assertNull(CountedInputReflection.read(new Object()));
    }

    private record RecordStyleInput(Object ingredient, int count) {}

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
