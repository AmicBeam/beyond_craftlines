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
                slot("catalyst", "minecraft:item_stack"),
                slot("input_0", "minecraft:item_stack"),
                slot("input_1", "minecraft:item_stack"))));
    }

    @Test
    void doesNotInventSemanticGroupsFromUnnamedColumns()
    {
        assertEquals(List.of("ingredients", "ingredients", "ingredients", "ingredients"),
                JeiSlotGroupResolver.resolve(List.of(
                        slot("", "minecraft:item_stack"),
                        slot("", "minecraft:item_stack"),
                        slot("", "minecraft:item_stack"),
                        slot("", "minecraft:item_stack"))));
    }

    @Test
    void preservesNamedBidirectionalChemicalSlots()
    {
        assertEquals(List.of("left_input", "right_input"), JeiSlotGroupResolver.resolve(List.of(
                slot("leftInput", "mekanism:chemical"),
                slot("rightInput", "mekanism:chemical"))));
    }

    @Test
    void separatesUnnamedResourceTypesBeforePosition()
    {
        assertEquals(List.of("input_item_stack", "input_chemical"), JeiSlotGroupResolver.resolve(List.of(
                slot("", "minecraft:item_stack"),
                slot("", "mekanism:chemical"))));
    }

    private static JeiSlotGroupResolver.Slot slot(String name, String type)
    { return new JeiSlotGroupResolver.Slot(name, Set.of(type)); }
}
