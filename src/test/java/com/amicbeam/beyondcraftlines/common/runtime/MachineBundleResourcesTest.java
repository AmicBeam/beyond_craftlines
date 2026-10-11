package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class MachineBundleResourcesTest
{
    @Test void inputOnlyOxygenIsLeftInsideMachineWithoutInventingStock()
    {
        IStackKey<?> oxygen = MachineRecipeBundleTest.key("oxygen");
        FakeMachine machine = new FakeMachine();
        machine.inputs.put(oxygen, 200L); // An input-only chemical tank exposes no extractable stack.
        MachineRecipeBundle bundle = new MachineRecipeBundle(0, 1, 1, List.of(new KeyAmount(oxygen, 200)));

        assertTrue(MachineBundleResources.collect(List.of(new KeyAmount(oxygen, 200)), bundle, machine).isEmpty());
        assertEquals(200L, machine.inputs.get(oxygen));
        assertEquals(0, machine.extractionCalls);
    }

    @Test void surplusAboveRetainedQuotaCanBeRecoveredBeforeFinalCompletion()
    {
        IStackKey<?> intermediate = MachineRecipeBundleTest.key("intermediate");
        FakeMachine machine = new FakeMachine(); machine.outputs.put(intermediate, 300L);
        MachineRecipeBundle bundle = new MachineRecipeBundle(0, 1, 1, List.of(new KeyAmount(intermediate, 200)));
        assertEquals(List.of(new KeyAmount(intermediate, 100)), MachineBundleResources.collect(
                List.of(new KeyAmount(intermediate, 300)), bundle, machine));
        assertEquals(200L, machine.outputs.get(intermediate));
        assertTrue(MachineBundleResources.collect(List.of(new KeyAmount(intermediate, 300)), bundle, machine).isEmpty());
    }

    @Test void unrelatedByproductIsRecoveredAndHeldChemicalIsReleasedOnCompletion()
    {
        IStackKey<?> chemical = MachineRecipeBundleTest.key("chemical");
        IStackKey<?> byproduct = MachineRecipeBundleTest.key("byproduct");
        FakeMachine machine = new FakeMachine();
        machine.outputs.put(chemical, 200L); machine.outputs.put(byproduct, 7L);
        MachineRecipeBundle bundle = new MachineRecipeBundle(0, 1, 1, List.of(new KeyAmount(chemical, 200)));
        assertEquals(List.of(new KeyAmount(byproduct, 7)), MachineBundleResources.collect(
                List.of(new KeyAmount(chemical, 200), new KeyAmount(byproduct, 7)), bundle, machine));
        assertEquals(200L, machine.outputs.get(chemical));
        assertEquals(List.of(new KeyAmount(chemical, 200)), MachineBundleResources.collect(
                List.of(new KeyAmount(chemical, 200)), null, machine));
    }

    private static final class FakeMachine implements MachineBundleResources.Access
    {
        final Map<IStackKey<?>, Long> inputs = new HashMap<>(), outputs = new HashMap<>();
        int extractionCalls;
        public long extractable(IStackKey<?> key) { return outputs.getOrDefault(key, 0L); }
        public List<KeyAmount> extract(IStackKey<?> key, long amount)
        {
            extractionCalls++;
            long taken = Math.min(amount, extractable(key));
            outputs.put(key, extractable(key) - taken);
            return taken == 0 ? List.of() : List.of(new KeyAmount(key, taken));
        }

    }
}
