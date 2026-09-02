package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExplicitRootOutputMatchTest
{
    @Test void acceptsConcreteDynamicOutputWhenTheDeclaredResultHasOnlyTheBaseState()
    {
        Blade target = new Blade("slashblade:slashblade", "awakened+refine12");
        Blade declared = new Blade("slashblade:slashblade", "base");

        assertTrue(ExplicitRootOutputMatch.matches(target, List.of(declared),
                Blade::sameComponents, Blade::sameItem));
    }

    @Test void rejectsARequestedItemOutsideTheDeclaredOutputFamily()
    {
        Blade target = new Blade("slashblade:slashblade", "awakened");
        Blade declared = new Blade("slashblade:proudsoul", "base");

        assertFalse(ExplicitRootOutputMatch.matches(target, List.of(declared),
                Blade::sameComponents, Blade::sameItem));
    }

    @Test void retainsExactComponentMatchingForOrdinaryDeclaredOutputs()
    {
        Blade target = new Blade("slashblade:slashblade", "awakened");

        assertTrue(ExplicitRootOutputMatch.matches(target,
                List.of(new Blade("slashblade:slashblade", "awakened")),
                Blade::sameComponents, Blade::sameItem));
    }

    private record Blade(String item, String state)
    {
        private boolean sameItem(Blade other) { return item.equals(other.item); }
        private boolean sameComponents(Blade other)
        { return sameItem(other) && state.equals(other.state); }
    }
}
