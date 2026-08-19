package com.amicbeam.beyondcraftlines.common.structure;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TrialFluidMeasurementAccumulator
{
    private final Map<String, Long> inputs = new LinkedHashMap<>();
    private final Map<String, Long> outputs = new LinkedHashMap<>();

    public void addInput(String fluidId, long amount) { add(inputs, fluidId, amount); }
    public void addOutput(String fluidId, long amount) { add(outputs, fluidId, amount); }
    public Map<String, Long> inputs() { return Map.copyOf(inputs); }
    public Map<String, Long> outputs() { return Map.copyOf(outputs); }

    private static void add(Map<String, Long> values, String id, long amount)
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("fluid ID is required");
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        values.merge(id, amount, Math::addExact);
    }
}
