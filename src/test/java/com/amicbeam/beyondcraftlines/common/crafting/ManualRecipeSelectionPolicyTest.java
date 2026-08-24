package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManualRecipeSelectionPolicyTest
{
    @Test void emptyUnboundProvisionerAcceptsExactlyOneManualParent()
    {
        assertTrue(ManualRecipeSelectionPolicy.accepts(false, Set.of(), Set.of(), Set.of("malum:spirit_focusing")));
        assertFalse(ManualRecipeSelectionPolicy.accepts(false, Set.of(), Set.of(),
                Set.of("malum:spirit_focusing", "malum:weeping_well")));
    }

    @Test void manualFallbackIsUnavailableAfterAParentWasSelected()
    {
        assertFalse(ManualRecipeSelectionPolicy.accepts(false, Set.of(), Set.of("malum:spirit_focusing"),
                Set.of("malum:weeping_well")));
    }

    @Test void scannedCandidatesRetainTheirExistingMultiSelectBehavior()
    {
        Set<String> candidates = Set.of("create:mixing", "create:packing");
        assertTrue(ManualRecipeSelectionPolicy.accepts(false, candidates, Set.of(), candidates));
    }

    @Test void BoundMachineConfigurationNeverEnablesManualFallback()
    {
        assertFalse(ManualRecipeSelectionPolicy.accepts(true, Set.of(), Set.of(), Set.of("malum:spirit_focusing")));
    }
}
