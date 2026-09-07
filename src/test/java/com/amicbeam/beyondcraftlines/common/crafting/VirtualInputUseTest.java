package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VirtualInputUseTest
{
    @Test void unchangedRemainderIsPermanentlyReusable()
    {
        assertEquals(VirtualInputUse.REUSABLE,
                VirtualInputUse.fromRemainder(true, true, true, 0, 0));
    }

    @Test void damagedRemainderConsumesMeasuredDurability()
    {
        assertEquals(VirtualInputUse.durability(3),
                VirtualInputUse.fromRemainder(true, false, true, 4, 7));
    }

    @Test void nonDamageComponentMutationIsNotTreatedAsReusable()
    {
        assertEquals(VirtualInputUse.CONSUMED,
                VirtualInputUse.fromRemainder(true, false, false, 0, 0));
    }

    @Test void differentOrMissingRemainderIsConsumed()
    {
        assertEquals(VirtualInputUse.CONSUMED,
                VirtualInputUse.fromRemainder(false, false, true, 0, 1));
    }
}
