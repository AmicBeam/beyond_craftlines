package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProvisionerInputGroupSelectionTest
{
    @Test
    void emptySelectionAcceptsEveryAvailableGroup()
    {
        assertEquals(Set.of(ProvisionerInputGroupSelection.ALL),
                ProvisionerInputGroupSelection.accepted(
                        Set.of("ingredients", "activation_item"), Set.of()));
    }

    @Test
    void nonEmptySelectionRestrictsAcceptedGroups()
    {
        assertEquals(Set.of("activation_item"), ProvisionerInputGroupSelection.accepted(
                Set.of("ingredients", "activation_item"), Set.of("activation_item")));
    }

    @Test
    void singleGroupNeedsNoExplicitSelection()
    {
        assertEquals(Set.of(ProvisionerInputGroupSelection.ALL),
                ProvisionerInputGroupSelection.accepted(Set.of("ingredients"), Set.of("ingredients")));
    }
}
