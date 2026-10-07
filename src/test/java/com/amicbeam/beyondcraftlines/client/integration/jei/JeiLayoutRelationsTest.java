package com.amicbeam.beyondcraftlines.client.integration.jei;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class JeiLayoutRelationsTest
{
    @Test void recordsLinksForLegacyDirectLayoutReturn()
    {
        Object layout = new Object();
        Object input = new Object();
        Object output = new Object();
        JeiLayoutRelations.record(layout, List.of(input, output), List.of(List.of(input, output)));
        assertEquals(List.of(List.of(0, 1)), JeiLayoutRelations.links(layout));
    }

    @Test void newerJeiOptionalReturnAssociatesLinksWithTheActualLayout()
    {
        Object layout = new Object();
        Object input = new Object();
        Object output = new Object();
        var returned = Optional.of(layout);
        JeiLayoutRelations.record(returned, List.of(input, output), List.of(List.of(input, output)));
        assertEquals(List.of(List.of(0, 1)), JeiLayoutRelations.links(layout));
        assertThrows(IllegalArgumentException.class, () -> JeiLayoutRelations.links(returned));
    }

    @Test void optionalLayoutWithNoFocusLinksIsStillKnownAndMissingLayoutsFailClosed()
    {
        Object layout = new Object();
        JeiLayoutRelations.record(Optional.of(layout), List.of(new Object()), List.of());
        assertEquals(List.of(), JeiLayoutRelations.links(layout));
        JeiLayoutRelations.record(Optional.empty(), List.of(), List.of());
        JeiLayoutRelations.record(null, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> JeiLayoutRelations.links(new Object()));
        assertThrows(IllegalArgumentException.class, () -> JeiLayoutRelations.links(Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> JeiLayoutRelations.links(null));
    }
}
