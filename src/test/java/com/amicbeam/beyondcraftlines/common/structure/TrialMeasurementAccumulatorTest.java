package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrialMeasurementAccumulatorTest
{
    @Test
    void requiresCycleMeasurement()
    {
        TrialMeasurementAccumulator accumulator = new TrialMeasurementAccumulator();
        accumulator.addInput("minecraft:iron_ingot", 2);
        accumulator.addOutput("minecraft:iron_block", 1);
        assertThrows(IllegalStateException.class, accumulator::build);
    }

    @Test
    void rejectsInvalidMeasurements()
    {
        TrialMeasurementAccumulator accumulator = new TrialMeasurementAccumulator();
        assertThrows(IllegalArgumentException.class, () -> accumulator.addInput("", 1));
        assertThrows(IllegalArgumentException.class, () -> accumulator.addOutput("minecraft:stone", -1));
        assertThrows(IllegalArgumentException.class, () -> accumulator.addEnergy(-1));
        assertThrows(IllegalArgumentException.class, () -> accumulator.setCycleTicks(0));
    }
}
