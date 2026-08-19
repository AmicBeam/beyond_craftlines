package com.amicbeam.beyondcraftlines.common.structure;

import java.util.List;
import java.util.Map;

public final class TrialReportService
{
    private TrialReportService() {}

    public static CompiledBlueprint compile(BlueprintRecord record, TrialObservation observation)
    {
        if (record == null || record.snapshot() == null || observation == null)
            throw new IllegalArgumentException("trial input is required");
        if (record.snapshot().hash() == null || record.snapshot().hash().isBlank())
            throw new IllegalArgumentException("structure hash is invalid");
        return new CompiledBlueprint(
                record.id(),
                record.owner(),
                record.snapshot().hash(),
                merge(record.snapshot().itemTotals()),
                observation.inputs(),
                observation.outputs(),
                observation.fluidInputs(),
                observation.fluidOutputs(),
                observation.energyNet(),
                observation.cycleTicks(),
                1,
                1);
    }

    private static List<ResourceAmount> merge(Map<String, Integer> totals)
    {
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new ResourceAmount(net.minecraft.resources.ResourceLocation.parse(entry.getKey()), entry.getValue()))
                .toList();
    }
}
