package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.DynamicCraftingFixtures;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class ActualOutputCapacityTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }
    @Test void distinctComponentsNeedSeparateInventorySpaceAndSimulationDoesNotMutateSlots()
    {
        var a = DynamicCraftingFixtures.blade("one");
        var b = DynamicCraftingFixtures.blade("two");
        var incoming = List.of(new ActualOutputCapacity.StackAmount(a, 1), new ActualOutputCapacity.StackAmount(b, 1));
        assertFalse(ActualOutputCapacity.fitsStacks(List.of(ItemStack.EMPTY), incoming));
        assertTrue(ActualOutputCapacity.fitsStacks(List.of(ItemStack.EMPTY, ItemStack.EMPTY), incoming));
        var occupied = a.copy(); occupied.setCount(occupied.getMaxStackSize());
        assertFalse(ActualOutputCapacity.fitsStacks(List.of(occupied), List.of(new ActualOutputCapacity.StackAmount(b, 1))));
        assertEquals(occupied.getMaxStackSize(), occupied.getCount());
        assertEquals(1, a.getCount()); assertEquals(1, b.getCount());
    }
}
