package com.amicbeam.beyondcraftlines.common.structure;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TrialMeasurementAccumulator
{
    private final Map<String, Long> inputs = new LinkedHashMap<>();
    private final Map<String, Long> outputs = new LinkedHashMap<>();
    private final Map<String, Long> fluidInputs = new LinkedHashMap<>();
    private final Map<String, Long> fluidOutputs = new LinkedHashMap<>();
    private long energyNet;
    private long cycleTicks;

    public void addInput(String itemId, long amount) { add(inputs, itemId, amount); }
    public void addOutput(String itemId, long amount) { add(outputs, itemId, amount); }
    public void addFluidInput(String fluidId, long amount) { add(fluidInputs, fluidId, amount); }
    public void addFluidOutput(String fluidId, long amount) { add(fluidOutputs, fluidId, amount); }
    public void addEnergy(long amount)
    {
        if (amount < 0) throw new IllegalArgumentException("energy amount must not be negative");
        energyNet = Math.addExact(energyNet, amount);
    }
    public void setCycleTicks(long value)
    {
        if (value < 1) throw new IllegalArgumentException("cycleTicks must be positive");
        cycleTicks = value;
    }
    public TrialObservation build()
    {
        if (cycleTicks < 1) throw new IllegalStateException("cycleTicks has not been measured");
        return TrialObservation.fromMaps(inputs, outputs, fluidInputs, fluidOutputs, energyNet, cycleTicks);
    }
    public Map<String, Long> inputs() { return Map.copyOf(inputs); }
    public Map<String, Long> outputs() { return Map.copyOf(outputs); }
    public Map<String, Long> fluidInputs() { return Map.copyOf(fluidInputs); }
    public Map<String, Long> fluidOutputs() { return Map.copyOf(fluidOutputs); }
    public long energyNet() { return energyNet; }
    public long cycleTicks() { return cycleTicks; }

    private static void add(Map<String, Long> values, String id, long amount)
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("resource ID is required");
        if (amount < 0) throw new IllegalArgumentException("measurement amount must not be negative");
        values.merge(id, amount, Math::addExact);
    }
}
