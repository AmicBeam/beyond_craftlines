package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FireworkRocketVariantsTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }
    @Test void durationsHaveSeparateSlotsAndRealVanillaOutputs()
    {
        for (int flight = 1; flight <= 3; flight++)
        {
            var output = DynamicCraftingFixtures.rocket(flight);
            var variant = new FireworkRocketCraftingVariant(DynamicCraftingFixtures.source(), output, flight);
            assertEquals(flight + 1, RecipeIngredientResolver.ingredients(variant).size());
            assertEquals(3, output.getCount());
            assertTrue(variant.acceptsOutput(output));
            var wrongCount = output.copy(); wrongCount.setCount(1);
            assertFalse(variant.acceptsOutput(wrongCount));
            assertTrue(DynamicCraftingFixtures.matches(variant, flight));
            assertTrue(StackKeyMatch.exact(DynamicCraftingFixtures.key(output),
                    DynamicCraftingFixtures.key(DynamicCraftingFixtures.assemble(variant, flight))));
            for (int other = 1; other <= 3; other++)
                if (other != flight)
                {
                    assertFalse(DynamicCraftingFixtures.matches(variant, other));
                    assertFalse(variant.acceptsOutput(DynamicCraftingFixtures.rocket(other)));
                    assertFalse(StackKeyMatch.exact(DynamicCraftingFixtures.key(output),
                            DynamicCraftingFixtures.key(DynamicCraftingFixtures.rocket(other))));
                }
        }
    }
    @Test void variantIdsAreStableDistinctAndSourceBound()
    {
        var one = FireworkRocketRecipeVariants.variantId("minecraft:firework_rocket", 1);
        assertEquals(one, FireworkRocketRecipeVariants.variantId("minecraft:firework_rocket", 1));
        assertNotEquals(one, FireworkRocketRecipeVariants.variantId("minecraft:firework_rocket", 2));
        assertNotEquals(one, FireworkRocketRecipeVariants.variantId("other:rocket", 1));
        assertThrows(IllegalArgumentException.class, () -> FireworkRocketRecipeVariants.samples(4));
    }
}
