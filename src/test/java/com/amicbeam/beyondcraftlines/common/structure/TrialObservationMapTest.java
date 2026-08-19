package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrialObservationMapTest
{
    @Test
    void rejectsMissingOrNegativeMeasurements()
    {
        assertThrows(IllegalArgumentException.class,
                () -> TrialObservation.fromMaps(null, Map.of(), 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> TrialObservation.fromMaps(Map.of("minecraft:iron_ingot", -1L), Map.of(), 0, 1));
    }
}
