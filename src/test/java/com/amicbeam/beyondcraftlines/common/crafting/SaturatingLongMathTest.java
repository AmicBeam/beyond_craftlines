package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SaturatingLongMathTest
{
    @Test
    void additionAndMultiplicationCapInsteadOfWrapping()
    {
        assertEquals(Long.MAX_VALUE, SaturatingLongMath.add(Long.MAX_VALUE - 3, 4));
        assertEquals(Long.MAX_VALUE, SaturatingLongMath.multiply(Long.MAX_VALUE / 2 + 1, 2));
        assertEquals(42, SaturatingLongMath.multiply(6, 7));
    }

    @Test
    void ceilDivisionWorksAtLongMaxValue()
    {
        assertEquals(Long.MAX_VALUE, SaturatingLongMath.ceilDiv(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE / 2 + 1, SaturatingLongMath.ceilDiv(Long.MAX_VALUE, 2));
        assertEquals(3, SaturatingLongMath.ceilDiv(9, 4));
    }

    @Test
    void quantitiesRemainLongPastIntegerMaxValue()
    {
        long order = (long) Integer.MAX_VALUE + 1_000_000;
        assertEquals(order, SaturatingLongMath.multiply(order, 1));
        assertEquals(order, SaturatingLongMath.ceilDiv(order, 1));
    }
}
