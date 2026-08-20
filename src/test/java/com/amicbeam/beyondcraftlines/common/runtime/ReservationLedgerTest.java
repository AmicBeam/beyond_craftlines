package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationLedgerTest
{
    @Test
    void subtractsConsumedMaterialsWithoutMutatingSource()
    {
        LinkedHashMap<String, Long> source = new LinkedHashMap<>(Map.of("red", 5L, "blue", 2L));
        Map<String, Long> result = ReservationLedger.subtract(source, Map.of("red", 3L, "blue", 2L));

        assertEquals(Map.of("red", 2L), result);
        assertEquals(5L, source.get("red"));
    }

    @Test
    void rejectsEscrowUnderflow()
    { assertThrows(IllegalStateException.class,
            () -> ReservationLedger.subtract(Map.of("red", 1L), Map.of("red", 2L))); }
}
