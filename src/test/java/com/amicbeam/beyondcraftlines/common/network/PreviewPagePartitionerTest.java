package com.amicbeam.beyondcraftlines.common.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PreviewPagePartitionerTest
{
    @Test
    void splitsIndependentListsWithoutDroppingTheirTails()
    {
        List<Integer> first = List.of(1, 2, 3, 4, 5);
        List<String> second = List.of("a", "b", "c", "d");
        var pages = PreviewPagePartitioner.partition(first, second, 3);

        assertEquals(2, pages.size());
        assertEquals(first, pages.stream().flatMap(page -> page.first().stream()).toList());
        assertEquals(second, pages.stream().flatMap(page -> page.second().stream()).toList());
        assertTrue(pages.stream().allMatch(page -> page.first().size() <= 3 && page.second().size() <= 3));
    }

    @Test
    void longerSideDeterminesPageCountWhileOtherSideUsesEmptyPages()
    {
        var pages = PreviewPagePartitioner.partition(List.of(1), List.of("a", "b", "c", "d", "e"), 2);

        assertEquals(3, pages.size());
        assertEquals(List.of(1), pages.getFirst().first());
        assertEquals(List.of(), pages.get(1).first());
        assertEquals(List.of(), pages.get(2).first());
    }

    @Test
    void splitsMaterialSummaryAsAThirdIndependentStream()
    {
        List<Integer> first = List.of(1);
        List<String> second = List.of("a", "b");
        List<Long> materials = List.of(10L, 20L, 30L, 40L, 50L);
        var pages = PreviewPagePartitioner.partition(first, second, materials, 2);

        assertEquals(3, pages.size());
        assertEquals(materials, pages.stream().flatMap(page -> page.third().stream()).toList());
        assertTrue(pages.stream().allMatch(page -> page.third().size() <= 2));
    }

    @Test
    void emptyInputsStillProduceOneEmptyPage()
    {
        var pages = PreviewPagePartitioner.partition(List.of(), List.of(), 256);

        assertEquals(1, pages.size());
        assertTrue(pages.getFirst().first().isEmpty());
        assertTrue(pages.getFirst().second().isEmpty());
    }

    @Test
    void returnedPagesDoNotAliasMutableInputs()
    {
        List<Integer> first = new ArrayList<>(List.of(1, 2, 3));
        var pages = PreviewPagePartitioner.partition(first, List.of(), 2);
        first.set(0, 99);

        assertEquals(List.of(1, 2), pages.getFirst().first());
    }

    @Test
    void rejectsNonPositivePageSizes()
    {
        assertThrows(IllegalArgumentException.class,
                () -> PreviewPagePartitioner.partition(List.of(), List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewPagePartitioner.partition(List.of(), List.of(), -1));
    }
}
