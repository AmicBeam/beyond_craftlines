package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CraftlineProvisionerParticipationTest
{
    @Test
    void mixedRouteActivatesOnlyProvisionersActuallyOccupiedByTheStep()
    {
        assertTrue(ProvisionerParticipationLogic.shouldActivate(false, true),
                "a mixed route marks the wait as non-provisioner but still occupies this endpoint");
        assertTrue(ProvisionerParticipationLogic.shouldActivate(true, true));
        assertFalse(ProvisionerParticipationLogic.shouldActivate(false, false));
        assertFalse(ProvisionerParticipationLogic.shouldActivate(true, false),
                "an all-provisioner wait must not activate provisioners absent from its occupied endpoints");
    }
}
