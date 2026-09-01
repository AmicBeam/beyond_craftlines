package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

final class ClientRecipePlannerRecipeLookupTest
{
    @Test
    void findsRecipeWhenOnlyTheReconstructedOutputRecognizesTheProtocolTarget()
    {
        List<String> recipes = new ArrayList<>();
        recipes.add(null);
        Map<String, List<String>> index = new LinkedHashMap<>();
        index.put("reconstructed", recipes);

        assertSame(recipes, SymmetricMapLookup.first(index, "protocol",
                (requested, reconstructed) -> requested.equals("protocol")
                        && reconstructed.equals("reconstructed")));
    }
}
