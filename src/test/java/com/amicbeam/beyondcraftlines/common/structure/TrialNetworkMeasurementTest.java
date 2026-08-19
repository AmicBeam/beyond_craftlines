package com.amicbeam.beyondcraftlines.common.structure;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrialNetworkMeasurementTest
{
    @Test
    void recordsOnlyNegativeInventoryDeltaAsInputAndPositiveAsOutput()
    {
        TrialMeasurementAccumulator accumulator = new TrialMeasurementAccumulator();
        TrialNetworkMeasurement.apply(accumulator,
                new TrialNetworkSnapshot(1, Map.of("minecraft:iron_ingot", 10L),
                        Map.of("minecraft:water", 1000L), 500L),
                new TrialNetworkSnapshot(1, Map.of("minecraft:iron_ingot", 4L, "minecraft:gear", 2L),
                        Map.of("minecraft:water", 250L), 125L));

        assertEquals(6L, accumulator.inputs().get("minecraft:iron_ingot"));
        assertEquals(2L, accumulator.outputs().get("minecraft:gear"));
        assertEquals(750L, accumulator.fluidInputs().get("minecraft:water"));
        assertEquals(375L, accumulator.energyNet());
    }

    @Test
    void ignoresInventoryGrowthWhenSameResourceWasConsumedElsewhere()
    {
        TrialMeasurementAccumulator accumulator = new TrialMeasurementAccumulator();
        TrialNetworkMeasurement.apply(accumulator,
                new TrialNetworkSnapshot(1, Map.of("minecraft:iron_ingot", 10L), Map.of(), 0L),
                new TrialNetworkSnapshot(1, Map.of("minecraft:iron_ingot", 12L), Map.of(), 0L));

        assertEquals(2L, accumulator.outputs().get("minecraft:iron_ingot"));
        assertEquals(0, accumulator.inputs().size());
    }
}
