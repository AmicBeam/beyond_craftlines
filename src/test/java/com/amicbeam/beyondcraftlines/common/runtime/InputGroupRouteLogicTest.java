package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.ProvisionerInputGroupSelection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InputGroupRouteLogicTest
{
    @Test
    void blockingRitualRoutesAltarAndCapacityOnePedestalsToDirectMachines()
    {
        assertEquals(1, BlockingModeLogic.craftsToDispatch(true, 4));
        assertEquals(List.of("dark-altar"), selected(List.of(
                direct("dark-altar", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY),
                direct("legacy-wildcard", ProvisionerInputGroupSelection.WILDCARD_PRIORITY))));
        assertEquals(List.of("pedestal-a", "pedestal-b", "pedestal-c"), selected(List.of(
                direct("pedestal-c", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY),
                direct("pedestal-a", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY),
                direct("pedestal-b", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY),
                provisioner("pipe-buffer", ProvisionerInputGroupSelection.WILDCARD_PRIORITY))));
    }

    @Test
    void mixedRitualKeepsAltarDirectAndRoutesOfferingsThroughProvisioner()
    {
        assertEquals(List.of("dark-altar"), selected(List.of(
                direct("dark-altar", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY))));
        assertEquals(List.of("pedestal-pipe-buffer"), selected(List.of(
                direct("legacy-wildcard", ProvisionerInputGroupSelection.WILDCARD_PRIORITY),
                provisioner("pedestal-pipe-buffer", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY))));
    }

    @Test
    void configuredPrioritySelectsAmongEquallySpecificEndpoints()
    {
        assertEquals(List.of("preferred"), selected(List.of(
                direct("normal", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY, 0),
                direct("preferred", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY, 10),
                direct("also-normal", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY, 0))));
        assertEquals(List.of("exact"), selected(List.of(
                direct("exact", ProvisionerInputGroupSelection.EXPLICIT_PRIORITY, -10),
                direct("wildcard", ProvisionerInputGroupSelection.WILDCARD_PRIORITY, 100))));
    }

    @Test
    void oneAltarCanReceiveInputAndSpiritsInSuccessiveRounds()
    {
        assertTrue(InputGroupRouteLogic.canContinuePartialRound(1, 0, false),
                "the first input group is dispatched now");
        assertTrue(InputGroupRouteLogic.canContinuePartialRound(0, 0, true),
                "spirits blocked by the first planned resource are retried next tick");
        assertTrue(InputGroupRouteLogic.canContinuePartialRound(0, 1, false),
                "an input already present in the altar satisfies this round");
        assertFalse(InputGroupRouteLogic.canContinuePartialRound(0, 0, false),
                "a genuinely unwritable input still freezes the round");
    }

    private static InputGroupRouteLogic.Candidate<String> direct(String id, int priority)
    { return direct(id, priority, 0); }

    private static InputGroupRouteLogic.Candidate<String> direct(String id, int groupPriority, int endpointPriority)
    { return new InputGroupRouteLogic.Candidate<>(id, InputGroupRouteLogic.Kind.DIRECT_MACHINE,
            groupPriority, endpointPriority, id); }

    private static InputGroupRouteLogic.Candidate<String> provisioner(String id, int priority)
    { return new InputGroupRouteLogic.Candidate<>(id, InputGroupRouteLogic.Kind.PROVISIONER, priority, 0, id); }

    private static List<String> selected(List<InputGroupRouteLogic.Candidate<String>> candidates)
    { return InputGroupRouteLogic.preferred(candidates).stream()
            .map(InputGroupRouteLogic.Candidate::endpoint).toList(); }
}
