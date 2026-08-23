package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.ProvisionerInputGroupSelection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static InputGroupRouteLogic.Candidate<String> direct(String id, int priority)
    { return new InputGroupRouteLogic.Candidate<>(id, InputGroupRouteLogic.Kind.DIRECT_MACHINE, priority, id); }

    private static InputGroupRouteLogic.Candidate<String> provisioner(String id, int priority)
    { return new InputGroupRouteLogic.Candidate<>(id, InputGroupRouteLogic.Kind.PROVISIONER, priority, id); }

    private static List<String> selected(List<InputGroupRouteLogic.Candidate<String>> candidates)
    { return InputGroupRouteLogic.preferred(candidates).stream()
            .map(InputGroupRouteLogic.Candidate::endpoint).toList(); }
}
