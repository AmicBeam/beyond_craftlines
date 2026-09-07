package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmithingInputMatcherTest
{
    @Test
    void preservesTemplateBaseAndAdditionSlotOrder()
    {
        assertEquals(List.of(
                        List.of("template"),
                        List.of("base", "base_and_addition"),
                        List.of("addition", "base_and_addition")),
                SmithingInputMatcher.ordered(
                        List.of("addition", "base", "template", "unused", "base_and_addition"),
                        List.of(value -> value.equals("template"),
                                value -> value.startsWith("base"),
                                value -> value.contains("addition"))));
    }
}
