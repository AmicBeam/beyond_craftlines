package com.amicbeam.beyondcraftlines.common.structure;

import java.util.List;
import java.util.UUID;

public record CompiledBlueprint(
        UUID id,
        UUID owner,
        String structureHash,
        List<ResourceAmount> capex,
        List<ResourceAmount> inputs,
        List<ResourceAmount> outputs,
        List<FluidAmount> fluidInputs,
        List<FluidAmount> fluidOutputs,
        long energyNet,
        long cycleTicks,
        int version,
        int schemaVersion
) {
    public CompiledBlueprint {
        capex = List.copyOf(capex);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        fluidInputs = List.copyOf(fluidInputs);
        fluidOutputs = List.copyOf(fluidOutputs);
        if (cycleTicks < 1) throw new IllegalArgumentException("cycleTicks must be positive");
    }

    public CompiledBlueprint(UUID id, UUID owner, String structureHash,
                             List<ResourceAmount> capex, List<ResourceAmount> inputs,
                             List<ResourceAmount> outputs, long energyNet, long cycleTicks,
                             int version, int schemaVersion)
    {
        this(id, owner, structureHash, capex, inputs, outputs, List.of(), List.of(),
                energyNet, cycleTicks, version, schemaVersion);
    }
}
