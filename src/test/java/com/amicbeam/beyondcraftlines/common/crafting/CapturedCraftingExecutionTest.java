package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class CapturedCraftingExecutionTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }

    @Test void nativeMatcherReceivesTheCapturedGridAndEachRepeatedInputIsReserved()
    {
        var slab = DynamicCraftingFixtures.key(new ItemStack(Items.STONE_SLAB));
        var source = new CapturedCraftingSource("test:slabs", 3, 3, List.of(0, 8));
        var slot = new VirtualProvisionerRecipeRegistry.InputSlot("ingredients", List.of(new KeyAmount(slab, 1)));
        var descriptor = new VirtualProvisionerRecipeRegistry.Descriptor("crafting",
                DynamicCraftingFixtures.key(new ItemStack(Items.STONE)), 1, List.of(slot, slot), List.of(), List.of(), source);
        AtomicInteger matched = new AtomicInteger();
        AtomicInteger assembled = new AtomicInteger();
        CraftingRecipe nativeRecipe = (CraftingRecipe) Proxy.newProxyInstance(CraftingRecipe.class.getClassLoader(),
                new Class<?>[]{CraftingRecipe.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "matches" -> {
                        matched.incrementAndGet();
                        var input = (CraftingInput) args[0];
                        yield input.size() == 9 && input.getItem(0).is(Items.STONE_SLAB)
                                && input.getItem(8).is(Items.STONE_SLAB) && input.getItem(1).isEmpty();
                    }
                    case "assemble" -> { assembled.incrementAndGet(); yield new ItemStack(Items.STONE); }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertNull(SimulatedCrafting.prepareCaptured(UnifiedStorage.getEmpty(), nativeRecipe, null, descriptor,
                List.of(), Map.of(slab, 1L)));
        assertEquals(0, matched.get());
        var prepared = SimulatedCrafting.prepareCaptured(UnifiedStorage.getEmpty(), nativeRecipe, null, descriptor,
                List.of(), Map.of(slab, 2L));
        assertNotNull(prepared);
        assertEquals(1, matched.get());
        assertEquals(List.of(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L), prepared.slotAmounts());
        assertEquals(0, assembled.get(), "candidate preparation must never assemble a recipe");
        CraftingRecipe rejecting = (CraftingRecipe) Proxy.newProxyInstance(CraftingRecipe.class.getClassLoader(),
                new Class<?>[]{CraftingRecipe.class}, (proxy, method, args) -> {
                    if (method.getName().equals("matches")) return false;
                    throw new AssertionError("a rejected recipe must not assemble or consume anything");
                });
        assertNull(SimulatedCrafting.prepareCaptured(UnifiedStorage.getEmpty(), rejecting, null, descriptor,
                List.of(), Map.of(slab, 2L)));

    }

    @Test void captureCannotTurnAForeignMachineOrBulkCountIntoNativeCrafting()
    {
        var input = DynamicCraftingFixtures.key(new ItemStack(Items.STONE_SLAB));
        var output = DynamicCraftingFixtures.key(new ItemStack(Items.STONE));
        var source = new CapturedCraftingSource("test:slabs", 1, 1, List.of(0));
        var slot = new VirtualProvisionerRecipeRegistry.InputSlot("ingredients", List.of(new KeyAmount(input, 2)));
        assertThrows(IllegalArgumentException.class, () -> new VirtualProvisionerRecipeRegistry.Descriptor(
                "crafting", output, 1, List.of(slot), List.of(), List.of(), source));
        var single = new VirtualProvisionerRecipeRegistry.InputSlot("ingredients", List.of(new KeyAmount(input, 1)));
        assertThrows(IllegalArgumentException.class, () -> new VirtualProvisionerRecipeRegistry.Descriptor(
                "test:machine", output, 1, List.of(single), List.of(), List.of(), source));
    }
}
