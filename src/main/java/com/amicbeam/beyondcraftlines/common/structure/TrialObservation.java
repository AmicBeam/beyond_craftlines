package com.amicbeam.beyondcraftlines.common.structure;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record TrialObservation(
        List<ResourceAmount> inputs,
        List<ResourceAmount> outputs,
        List<FluidAmount> fluidInputs,
        List<FluidAmount> fluidOutputs,
        long energyNet,
        long cycleTicks
) {
    public TrialObservation {
        if (inputs == null || outputs == null || fluidInputs == null || fluidOutputs == null)
            throw new IllegalArgumentException("measurements are required");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        fluidInputs = List.copyOf(fluidInputs);
        fluidOutputs = List.copyOf(fluidOutputs);
        if (energyNet < 0) throw new IllegalArgumentException("energyNet must not be negative");
        if (cycleTicks < 1) throw new IllegalArgumentException("cycleTicks must be positive");
    }

    public TrialObservation(List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                            long energyNet, long cycleTicks)
    {
        this(inputs, outputs, List.of(), List.of(), energyNet, cycleTicks);
    }

    public static TrialObservation fromMaps(Map<String, Long> inputs, Map<String, Long> outputs,
                                            Map<String, Long> fluidInputs, Map<String, Long> fluidOutputs,
                                            long energyNet, long cycleTicks)
    {
        return new TrialObservation(toItems(inputs), toItems(outputs), toFluids(fluidInputs),
                toFluids(fluidOutputs), energyNet, cycleTicks);
    }

    public static TrialObservation fromMaps(Map<String, Long> inputs, Map<String, Long> outputs,
                                            long energyNet, long cycleTicks)
    {
        return fromMaps(inputs, outputs, Map.of(), Map.of(), energyNet, cycleTicks);
    }

    private static List<ResourceAmount> toItems(Map<String, Long> values)
    {
        List<ResourceAmount> result = new ArrayList<>();
        if (values == null) throw new IllegalArgumentException("measurements are required");
        values.forEach((id, amount) -> {
            if (id == null || amount == null || amount < 0) throw new IllegalArgumentException("invalid item measurement");
            if (amount > 0) result.add(new ResourceAmount(ResourceLocation.parse(id), amount));
        });
        return result;
    }

    private static List<FluidAmount> toFluids(Map<String, Long> values)
    {
        List<FluidAmount> result = new ArrayList<>();
        if (values == null) throw new IllegalArgumentException("measurements are required");
        values.forEach((id, amount) -> {
            if (id == null || amount == null || amount < 0) throw new IllegalArgumentException("invalid fluid measurement");
            if (amount > 0) result.add(new FluidAmount(ResourceLocation.parse(id), amount));
        });
        return result;
    }
}
