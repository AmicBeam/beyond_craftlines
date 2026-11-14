package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import static org.junit.jupiter.api.Assertions.*;

final class OrderedSlotPlanTest
{
    /** Create's combined inventory: nine independent crafters, one item per crafter. */
    private static final class MechanicalCrafters implements OrderedSlotPlan.Inventory<String>
    {
        final String[] contents;
        final Map<String, Integer> probes = new HashMap<>();
        final BiPredicate<Integer, String> filter;
        MechanicalCrafters(int count) { this(count, (slot, key) -> true); }
        MechanicalCrafters(int count, BiPredicate<Integer, String> filter)
        { contents = new String[count]; this.filter = filter; }
        public int slots() { return contents.length; }
        public String key(int slot) { return contents[slot]; }
        public long count(int slot) { return contents[slot] == null ? 0 : 1; }
        public long limit(int slot, String key) { return 1; }
        public boolean accepts(int slot, String key)
        {
            probes.merge(slot + ":" + key, 1, Integer::sum);
            return filter.test(slot, key);
        }
        void deliver(OrderedSlotPlan<String> plan, String key, int cell)
        {
            var allocations = plan.offer(key, 1, cell);
            assertEquals(1, allocations.size());
            var allocation = allocations.get(0);
            assertNull(contents[allocation.slot()]);
            assertEquals(1, allocation.amount());
            contents[allocation.slot()] = key;
        }
    }

    @Test void oneOrderPreservesFiveAAndFourBInAlternatingCrafterCells()
    {
        var inventory = new MechanicalCrafters(9);
        var plan = new OrderedSlotPlan<>(inventory);
        plan.beginRound();
        for (int cell = 0; cell < 9; cell++) inventory.deliver(plan, cell % 2 == 0 ? "A" : "B", cell);
        assertArrayEquals(new String[]{"A", "B", "A", "B", "A", "B", "A", "B", "A"}, inventory.contents);
    }

    @Test void originalEmptyCellsRemainEmptyIncludingLeadingAndWholeEmptyRow()
    {
        var inventory = new MechanicalCrafters(9);
        var plan = new OrderedSlotPlan<>(inventory);
        plan.beginRound();
        inventory.deliver(plan, "A", 1);
        inventory.deliver(plan, "B", 6);
        inventory.deliver(plan, "A", 8);
        assertArrayEquals(new String[]{null, "A", null, null, null, null, "B", null, "A"}, inventory.contents);
    }

    @Test void sourceCellIsNotHardwiredToMachineSlotAndRejectedSlotsAreCached()
    {
        var inventory = new MechanicalCrafters(5, (slot, key) -> slot != 0 && slot != 2);
        var plan = new OrderedSlotPlan<>(inventory);
        plan.beginRound();
        inventory.deliver(plan, "A", 0);
        inventory.deliver(plan, "B", 1);
        assertArrayEquals(new String[]{null, "A", null, "B", null}, inventory.contents);
        Map<String, Integer> firstProbes = Map.copyOf(inventory.probes);
        // Another craft in the same order refreshes contents, without probing compatibility again.
        Arrays.fill(inventory.contents, null);
        plan.beginRound();
        inventory.deliver(plan, "A", 0);
        inventory.deliver(plan, "B", 1);
        assertEquals(firstProbes, inventory.probes);
        assertTrue(inventory.probes.values().stream().allMatch(count -> count == 1));
    }

    @Test void oneSlotCannotBeOverbookedByDifferentMaterialsInOneRound()
    {
        var inventory = new MechanicalCrafters(1);
        var plan = new OrderedSlotPlan<>(inventory);
        plan.beginRound();
        assertEquals(List.of(new OrderedSlotPlan.Allocation(0, 1)), plan.offer("A", 1, -1));
        assertTrue(plan.offer("B", 1, -1).isEmpty());
        assertTrue(plan.offer("A", 1, -1).isEmpty());
        plan.beginRound(); // A was consumed; B may now use the same single slot.
        assertEquals(List.of(new OrderedSlotPlan.Allocation(0, 1)), plan.offer("B", 1, -1));
        assertTrue(inventory.probes.values().stream().allMatch(count -> count == 1));
    }

    @Test void aSlotRejectedByTheFirstIngredientCanStillReceiveTheSecondIngredient()
    {
        var inventory = new MechanicalCrafters(2, (slot, key) -> key.equals("A") ? slot == 1 : slot == 0);
        var plan = new OrderedSlotPlan<>(inventory);
        plan.beginRound();
        inventory.deliver(plan, "A", 0);
        inventory.deliver(plan, "B", 1);
        assertArrayEquals(new String[]{"B", "A"}, inventory.contents);
        assertTrue(inventory.probes.values().stream().allMatch(count -> count == 1));
    }

    @Test void occupiedGridSlotIsRetriedWithoutRedirectingOrCreditingSameItemElsewhere()
    {
        var inventory = new MechanicalCrafters(3);
        inventory.contents[0] = "foreign";
        inventory.contents[2] = "A";
        var plan = new OrderedSlotPlan<>(inventory);
        plan.beginRound();
        assertTrue(plan.offer("A", 1, 0).isEmpty());
        assertTrue(plan.hasAssignment(0));
        inventory.contents[0] = null;
        plan.beginRound();
        assertEquals(List.of(new OrderedSlotPlan.Allocation(0, 1)), plan.offer("A", 1, 0));
        assertEquals(1, inventory.probes.get("0:A"));
    }

    @Test void independentOrdersRecheckCompatibilityAndRepeatedAUsesDistinctGridCells()
    {
        var inventory = new MechanicalCrafters(3);
        var first = new OrderedSlotPlan<>(inventory);
        first.beginRound();
        inventory.deliver(first, "A", 0);
        inventory.deliver(first, "A", 1);
        inventory.deliver(first, "B", 2);
        assertArrayEquals(new String[]{"A", "A", "B"}, inventory.contents);
        Arrays.fill(inventory.contents, null);
        var second = new OrderedSlotPlan<>(inventory);
        second.beginRound();
        inventory.deliver(second, "A", 0);
        assertEquals(2, inventory.probes.get("0:A"));
    }

    @Test void anotherRecipeStepInTheSameOrderReusesProbesWithFreshSlotAssignments()
    {
        var inventory = new MechanicalCrafters(2);
        var first = new OrderedSlotPlan<>(inventory);
        first.beginRound();
        inventory.deliver(first, "A", 0);
        inventory.deliver(first, "B", 1);
        var probes = Map.copyOf(inventory.probes);
        Arrays.fill(inventory.contents, null);
        var nextStep = first.newLayout();
        nextStep.beginRound();
        inventory.deliver(nextStep, "A", 0);
        inventory.deliver(nextStep, "B", 1);
        assertEquals(probes, inventory.probes);
        assertArrayEquals(new String[]{"A", "B"}, inventory.contents);
    }
}
