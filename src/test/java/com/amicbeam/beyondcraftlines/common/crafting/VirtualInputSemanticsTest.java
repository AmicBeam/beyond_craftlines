package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class VirtualInputSemanticsTest
{
    @Test void cookingContainerIsConsumedAndRoutedSeparately()
    {
        var decision = VirtualInputSemantics.classify(true, false, false);
        assertTrue(decision.included());
        assertEquals("container", decision.inputGroup());
        assertEquals(VirtualInputUse.Kind.CONSUMED, decision.use().kind());
    }

    @Test void publicToolAccessorUsesDurabilityCapacity()
    {
        var decision = VirtualInputSemantics.classify(false, true, false);
        assertEquals("tool", decision.inputGroup());
        assertEquals(VirtualInputUse.Kind.DURABILITY, decision.use().kind());
        assertEquals(1, decision.use().damagePerCraft());
    }

    @Test void unrecognizedWorkstationCatalystIsNotARequiredMaterial()
    { assertFalse(VirtualInputSemantics.classify(false, false, true).included()); }
}
