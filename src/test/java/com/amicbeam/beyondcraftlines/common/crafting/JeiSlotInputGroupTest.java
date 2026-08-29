package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class JeiSlotInputGroupTest
{
    @Test void preservesSemanticSlotNames()
    {
        assertEquals("catalyst", JeiSlotInputGroup.fromSlotName("catalyst"));
        assertEquals("activation_item", JeiSlotInputGroup.fromSlotName("activationItem"));
        assertEquals("chemical_input", JeiSlotInputGroup.fromSlotName("chemical_input"));
    }

    @Test void foldsNumberedGenericInputsIntoIngredients()
    {
        assertEquals("ingredients", JeiSlotInputGroup.fromSlotName("input_0"));
        assertEquals("ingredients", JeiSlotInputGroup.fromSlotName("ingredient12"));
        assertEquals("ingredients", JeiSlotInputGroup.fromSlotName(""));
    }

    @Test void sanitizesUntrustedJeiSlotNames()
    {
        assertEquals("altar_item", JeiSlotInputGroup.fromSlotName(" Altar Item #2 "));
        assertTrue(JeiSlotInputGroup.isValid("altar_item"));
        assertFalse(JeiSlotInputGroup.isValid("bad|group"));
    }
}
