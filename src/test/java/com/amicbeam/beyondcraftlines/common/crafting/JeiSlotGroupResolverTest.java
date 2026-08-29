package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JeiSlotGroupResolverTest
{
    @Test
    void keepsNamedCatalystSeparateFromNumberedIngredients()
    {
        assertEquals(List.of("catalyst", "ingredients", "ingredients"), JeiSlotGroupResolver.resolve(List.of(
                slot("catalyst", "minecraft:item_stack", 63),
                slot("input_0", "minecraft:item_stack", 20),
                slot("input_1", "minecraft:item_stack", 104))));
    }

    @Test
    void separatesUnnamedMalumStyleColumns()
    {
        assertEquals(List.of("input_left", "input_left", "input_right", "input_center"),
                JeiSlotGroupResolver.resolve(List.of(
                        slot("", "minecraft:item_stack", 20),
                        slot("", "minecraft:item_stack", 20),
                        slot("", "minecraft:item_stack", 104),
                        slot("", "minecraft:item_stack", 63))));
    }

    @Test
    void preservesNamedBidirectionalChemicalSlots()
    {
        assertEquals(List.of("left_input", "right_input"), JeiSlotGroupResolver.resolve(List.of(
                slot("leftInput", "mekanism:chemical", 25),
                slot("rightInput", "mekanism:chemical", 133))));
    }

    @Test
    void separatesUnnamedResourceTypesBeforePosition()
    {
        assertEquals(List.of("input_item_stack", "input_chemical"), JeiSlotGroupResolver.resolve(List.of(
                slot("", "minecraft:item_stack", 64),
                slot("", "mekanism:chemical", 68))));
    }

    private static JeiSlotGroupResolver.Slot slot(String name, String type, int x)
    { return new JeiSlotGroupResolver.Slot(name, Set.of(type), x); }
}
