package com.amicbeam.beyondcraftlines.common.structure;

import java.util.Map;

public final class TrialNetworkMeasurement
{
    private TrialNetworkMeasurement() {}

    public static void apply(TrialMeasurementAccumulator accumulator,
                             TrialNetworkSnapshot before,
                             TrialNetworkSnapshot after)
    {
        if (accumulator == null || before == null || after == null)
            throw new IllegalArgumentException("measurement arguments are required");

        before.items().forEach((id, amount) -> {
            long delta = amount - after.items().getOrDefault(id, 0L);
            if (delta > 0) accumulator.addInput(id, delta);
        });
        after.items().forEach((id, amount) -> {
            long delta = amount - before.items().getOrDefault(id, 0L);
            if (delta > 0) accumulator.addOutput(id, delta);
        });
        before.fluids().forEach((id, amount) -> {
            long delta = amount - after.fluids().getOrDefault(id, 0L);
            if (delta > 0) accumulator.addFluidInput(id, delta);
        });
        after.fluids().forEach((id, amount) -> {
            long delta = amount - before.fluids().getOrDefault(id, 0L);
            if (delta > 0) accumulator.addFluidOutput(id, delta);
        });
        long energyDelta = before.energy() - after.energy();
        if (energyDelta > 0) accumulator.addEnergy(energyDelta);
    }
}
