package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeIngredientResolverTest
{
    @Test void treatsMissingThirdPartyIngredientCollectionsAsEmpty()
    {
        assertTrue(RecipeIngredientResolver.safeCopy(null).isEmpty());
    }

    @Test void ignoresNullEntriesAndReturnsAnImmutableSnapshot()
    {
        List<String> source = new ArrayList<>(Arrays.asList("iron", null, "gold"));
        List<String> copy = RecipeIngredientResolver.safeCopy(source);
        source.set(0, "copper");

        assertEquals(List.of("iron", "gold"), copy);
    }
}
