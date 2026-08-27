package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProvisionerPollingLogicTest
{
    @Test void enabledActivationRestartsFromFirstBinding()
    {
        int cursor = ProvisionerPollingLogic.cursorOnActivation(true, 2);
        assertEquals(List.of(0, 1, 2, 3), ProvisionerPollingLogic.roundRobinOrder(4, cursor));
    }

    @Test void disabledActivationPreservesContinuousRoundRobin()
    {
        int cursor = ProvisionerPollingLogic.cursorOnActivation(false, 2);
        assertEquals(List.of(2, 3, 0, 1), ProvisionerPollingLogic.roundRobinOrder(4, cursor));
    }
}
