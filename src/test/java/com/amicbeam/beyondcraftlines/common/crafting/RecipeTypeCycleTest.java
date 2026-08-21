package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeTypeCycleTest
{
    private static final Set<String> ROTARY_TYPES = Set.of(
            "mekanism:condensentrating", "mekanism:decondensentrating");

    @Test
    void firstBindingSelectsTheFirstType()
    {
        assertEquals("mekanism:condensentrating", RecipeTypeCycle.next(ROTARY_TYPES, Set.of()));
    }

    @Test
    void repeatedBindingsCycleInStableOrder()
    {
        assertEquals("mekanism:decondensentrating", RecipeTypeCycle.next(
                ROTARY_TYPES, Set.of("mekanism:condensentrating")));
        assertEquals("mekanism:condensentrating", RecipeTypeCycle.next(
                ROTARY_TYPES, Set.of("mekanism:decondensentrating")));
    }

    @Test
    void legacyMultiTypeBindingRestartsAtTheFirstType()
    {
        assertEquals("mekanism:condensentrating", RecipeTypeCycle.next(ROTARY_TYPES, ROTARY_TYPES));
    }
}
