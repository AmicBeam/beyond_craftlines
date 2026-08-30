package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VirtualInputPolicyTest
{
    @Test void distinguishesReusableToolsFromConsumedCatalysts()
    {
        assertTrue(VirtualInputPolicy.reusable(false, false, true));
        assertTrue(VirtualInputPolicy.reusable(true, false, false));
        assertFalse(VirtualInputPolicy.reusable(true, true, false));
        assertFalse(VirtualInputPolicy.reusable(false, false, false));
    }

    @Test void findsComponentChoicesThatShareOneItemIdentity()
    { assertEquals(java.util.Set.of("potion"), VirtualInputPolicy.ambiguous(
            List.of("potion", "potion", "glass_bottle"))); }
}
