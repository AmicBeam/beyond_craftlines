package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClientRecipeLookupIndexTest
{
    @Test
    void fallsBackToProfileCompatibleOutputWhenExactComponentStateIsAbsent()
    {
        assertEquals(List.of("slashblade:puppet"), ClientRecipeLookupIndex.recipeIdsForOutput(
                "slashblade|white_sheath_components", "item|slashblade|slashblade:slashblade",
                Map.of("slashblade|puppet_components", List.of("slashblade:puppet")),
                Map.of("item|slashblade|slashblade:slashblade", List.of("slashblade:puppet"))));
    }

    @Test
    void keepsExactComponentRecipeAheadOfProfileFallback()
    {
        assertEquals(List.of("slashblade:white_sheath"), ClientRecipeLookupIndex.recipeIdsForOutput(
                "slashblade|white_sheath_components", "item|slashblade|slashblade:slashblade",
                Map.of("slashblade|white_sheath_components", List.of("slashblade:white_sheath")),
                Map.of("item|slashblade|slashblade:slashblade", List.of("slashblade:puppet"))));
    }

    @Test
    void doesNotUseCoarseCandidatesWithoutACompatibleProfile()
    {
        assertEquals(List.of(), ClientRecipeLookupIndex.recipeIdsForOutput(
                "minecraft|awkward_potion", "item|minecraft|minecraft:potion",
                Map.of("minecraft|water_potion", List.of("minecraft:water")), Map.of()));
    }
}
