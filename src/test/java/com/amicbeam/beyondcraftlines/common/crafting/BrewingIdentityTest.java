package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BrewingIdentityTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }
    @Test void waterAwkwardAndSplashKeepSeparateStockAndRecipeIdentities()
    {
        var water = DynamicCraftingFixtures.key(DynamicCraftingFixtures.potion(false, false));
        var awkward = DynamicCraftingFixtures.key(DynamicCraftingFixtures.potion(true, false));
        var splash = DynamicCraftingFixtures.key(DynamicCraftingFixtures.potion(true, true));
        assertFalse(StackKeyMatch.exact(water, awkward));
        assertFalse(StackKeyMatch.exact(awkward, splash));
        assertNotEquals(RecipeResourceResolver.resolutionKey(water), RecipeResourceResolver.resolutionKey(awkward));
        assertFalse(RecipeIoProfileRegistry.outputMatches("minecraft:brewing", water, awkward));
        assertEquals(3, VanillaRecipeBatching.outputAmount("minecraft:brewing", 1));
        assertTrue(VanillaRecipeBatching.validUploadedOutputAmount("minecraft:brewing", 3));
        assertFalse(VanillaRecipeBatching.validUploadedOutputAmount("minecraft:brewing", 1));
    }
}
