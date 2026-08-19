package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrialObservationTest
{
    @Test
    void copiesMeasurementsAndKeepsTiming()
    {
        TrialObservation observation = new TrialObservation(List.of(), List.of(), 120, 40);
        assertEquals(120, observation.energyNet());
        assertEquals(40, observation.cycleTicks());
    }

    @Test
    void rejectsInvalidMeasurements()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new TrialObservation(List.of(), List.of(), -1, 40));
        assertThrows(IllegalArgumentException.class,
                () -> new TrialObservation(List.of(), List.of(), 1, 0));
    }
}
