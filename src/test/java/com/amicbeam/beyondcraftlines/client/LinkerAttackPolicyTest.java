package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;

import static com.amicbeam.beyondcraftlines.client.LinkerAttackPolicy.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LinkerAttackPolicyTest
{
    @Test void opensKnownBoundMachinesAndCancelsMining()
    { assertEquals(OPEN_AND_CANCEL_ATTACK, LinkerAttackPolicy.decide(false, true, true)); }

    @Test void asksServerWhenBindingSnapshotHasNotArrived()
    { assertEquals(VERIFY_WITH_SERVER, LinkerAttackPolicy.decide(false, false, false)); }

    @Test void leavesKnownUnboundBlocksMineable()
    { assertEquals(IGNORE, LinkerAttackPolicy.decide(false, false, true)); }

    @Test void alwaysOpensProvisioners()
    { assertEquals(OPEN_AND_CANCEL_ATTACK, LinkerAttackPolicy.decide(true, false, false)); }
}
