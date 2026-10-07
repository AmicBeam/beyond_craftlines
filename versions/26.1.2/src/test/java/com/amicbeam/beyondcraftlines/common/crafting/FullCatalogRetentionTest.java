package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class FullCatalogRetentionTest
{
    @Test void retainsCompletedClientRecipesBeyondTheRequestCacheLimitAndClearsOnReload()
    {
        VirtualProvisionerRecipeRegistry.clear();
        try
        {
            var output = FullCatalogNetworkScopeTest.key("output");
            var input = FullCatalogNetworkScopeTest.key("input");
            var slots = List.of(new VirtualProvisionerRecipeRegistry.InputSlot(
                    "input", List.of(new KeyAmount(input, 1L))));
            var first = VirtualProvisionerRecipeRegistry.retainForClientCatalog(
                    VirtualProvisionerRecipeRegistry.register("test:machine", output, 1L, slots));
            var id = first.id().identifier();
            for (int amount = 2; amount <= 16_400; amount++)
                VirtualProvisionerRecipeRegistry.retainForClientCatalog(
                        VirtualProvisionerRecipeRegistry.register("test:machine", output, amount, slots));

            assertSame(first, VirtualProvisionerRecipeRegistry.find(id).orElseThrow());
            assertSame(first, VirtualProvisionerRecipeRegistry.register("test:machine", output, 1L, slots));
            assertNotNull(VirtualProvisionerRecipeRegistry.descriptor(first.value()));
            assertEquals(16_400, VirtualProvisionerRecipeRegistry.recipes().size());
            VirtualProvisionerRecipeRegistry.clear();
            assertTrue(VirtualProvisionerRecipeRegistry.find(id).isEmpty());
            assertTrue(VirtualProvisionerRecipeRegistry.recipes().isEmpty());
        }
        finally { VirtualProvisionerRecipeRegistry.clear(); }
    }
}
