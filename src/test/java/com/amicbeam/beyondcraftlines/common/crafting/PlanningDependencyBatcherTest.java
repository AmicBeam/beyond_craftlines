package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlanningDependencyBatcherTest
{
    @Test
    void eightNineSlotCompressionLevelsRemainEightBatchedDependencies()
    {
        long required = 1;
        for (int level = 0; level < 8; level++)
        {
            var slots = new ArrayList<PlanningDependencyBatcher.Entry<String>>();
            for (int slot = 0; slot < 9; slot++)
                slots.add(new PlanningDependencyBatcher.Entry<>("lower_level", required));
            var batched = PlanningDependencyBatcher.aggregate(slots);
            assertEquals(1, batched.size());
            required = batched.get("lower_level");
        }
        assertEquals(43_046_721L, required);
    }

    @Test
    void reusableInputsAreNotMultipliedByTheCraftCount()
    {
        long crafts = 43_046_721L;
        assertEquals(1L, PlanningDependencyBatcher.inputAmount(true, 1, crafts));
        assertEquals(crafts, PlanningDependencyBatcher.inputAmount(false, 1, crafts));
    }

    @Test
    void repeatedReusableSlotsRequireOneItemPerSlotNotOnePerCraft()
    {
        long crafts = 43_046_721L;
        var slots = java.util.List.of(
                new PlanningDependencyBatcher.Entry<>("tool", PlanningDependencyBatcher.inputAmount(true, 1, crafts)),
                new PlanningDependencyBatcher.Entry<>("tool", PlanningDependencyBatcher.inputAmount(true, 1, crafts)));
        assertEquals(2L, PlanningDependencyBatcher.aggregate(slots).get("tool"));
    }
}
