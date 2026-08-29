package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SymmetricMatchTest
{
    @Test
    void acceptsEquivalenceRecognizedOnlyByTheReconstructedSide()
    {
        assertTrue(SymmetricMatch.either("planned", "extracted",
                (left, right) -> left.equals("extracted") && right.equals("planned"),
                (left, right) -> false));
    }

    @Test
    void fallsBackToCompatibleTypeAndComponents()
    {
        assertTrue(SymmetricMatch.either("planned", "protocol",
                (left, right) -> false,
                (left, right) -> left.charAt(0) == right.charAt(0)));
        assertFalse(SymmetricMatch.either("planned", "other",
                (left, right) -> false,
                (left, right) -> left.charAt(0) == right.charAt(0)));
    }

    @Test
    void exactMatchRejectsTheSameItemWithDifferentComponents()
    {
        Key awakened = new Key("slashblade:slashblade", "awakened");
        Key broken = new Key("slashblade:slashblade", "broken");

        assertTrue(awakened.item().equals(broken.item()),
                "coarse item matching reproduces the old candidate leak");
        assertFalse(SymmetricMatch.exact(awakened, broken, (left, right) ->
                left.item().equals(right.item()) && left.components().equals(right.components())));
        assertTrue(SymmetricMatch.exact(awakened,
                new Key("slashblade:slashblade", "awakened"), (left, right) ->
                        left.item().equals(right.item()) && left.components().equals(right.components())));
    }

    private record Key(String item, String components) {}
}
