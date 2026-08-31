package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DurabilityInputMathTest
{
    @Test void usesEachToolsRemainingCapacityBeforeRequestingAnother()
    {
        assertEquals(1, DurabilityInputMath.requiredTools(64, 1, 64));
        assertEquals(2, DurabilityInputMath.requiredTools(65, 1, 64));
        assertEquals(4, DurabilityInputMath.requiredTools(65, 2, 64));
    }

    @Test void saturatesInsteadOfOverflowing()
    { assertEquals(Long.MAX_VALUE, DurabilityInputMath.requiredTools(Long.MAX_VALUE, 2, 1)); }
}
