package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class IdealRequestQuantityTest
{
    @Test
    void suggestsTheNextCompleteRootBatch()
    {
        assertEquals(6, IdealRequestQuantity.suggest("root", 5,
                List.of(new IdealRequestQuantity.Batch("root", 3, 2, Map.of()))));
    }

    @Test
    void alignsNestedRecipeBatchesToAvoidIntermediateSurplus()
    {
        var root = new IdealRequestQuantity.Batch("root", 3, 21, Map.of("child", 42L));
        var child = new IdealRequestQuantity.Batch("child", 4, 11, Map.of());

        assertEquals(66, IdealRequestQuantity.suggest("root", 63, List.of(child, root)));
    }

    @Test
    void omitsSuggestionWhenRequestIsAlreadyAligned()
    {
        assertEquals(0, IdealRequestQuantity.suggest("root", 6,
                List.of(new IdealRequestQuantity.Batch("root", 3, 2, Map.of()))));
    }

    @Test
    void refusesOverflowedSuggestions()
    {
        assertEquals(0, IdealRequestQuantity.suggest("root", Long.MAX_VALUE,
                List.of(new IdealRequestQuantity.Batch("root", Long.MAX_VALUE, 1, Map.of()))));
    }
}
