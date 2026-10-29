package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Blade state fixture: validates the shipped policy, not a substitute for loading SlashBlade itself. */
final class WhiteSheathOutputPolicyTest
{
    @BeforeAll static void bootstrap() { DynamicCraftingFixtures.bootstrap(); }
    @Test void actualBladeStateIsAcceptedOnlyForItsConfiguredProducer() throws Exception
    {
        String profile;
        try (var stream = getClass().getResourceAsStream("/data/beyond_craftlines/recipe_io_profiles/slashblade.json"))
        { profile = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        RecipeIoProfileRegistry.applySyncedEntries(List.of(profile));
        try
        {
            var template = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("white_sheath_jei"));
            var actual = DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("white_sheath_assembled"));
            assertFalse(StackKeyMatch.exact(template, actual));
            var step = new RecipePlan.Step(DynamicCraftingFixtures.id("slashblade:white_sheath"),
                    "crafting", template, 1, 1, List.of(), List.of());
            assertTrue(DynamicOutputRuntimeMatch.matches(template, actual, List.of(step)));
            assertFalse(DynamicOutputRuntimeMatch.matches(template, actual, List.of()));
            assertFalse(RecipeIoProfileRegistry.outputMatches("minecraft:firework_rocket", template, actual));
            assertFalse(RecipeIoProfileRegistry.outputMatches("minecraft:brewing", template, actual));
            assertTrue(StackKeyMatch.exact(actual, DynamicCraftingFixtures.key(DynamicCraftingFixtures.blade("white_sheath_assembled"))));
        }
        finally { RecipeIoProfileRegistry.applySyncedEntries(List.of()); }
    }
}
