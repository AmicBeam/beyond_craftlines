package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class RecipeCategoryBlacklistTest
{
    @AfterEach void disconnect() { RecipeCategoryBlacklist.clearSynced(); }

    @Test void defaultsOnlyExcludeCreatesGeneratedShapedAndShapelessCategories()
    {
        assertEquals(List.of("create:automatic_shaped", "create:automatic_shapeless"), RecipeCategoryBlacklist.DEFAULTS);
        RecipeCategoryBlacklist.applySynced(RecipeCategoryBlacklist.DEFAULTS);
        for (String blocked : RecipeCategoryBlacklist.DEFAULTS)
        {
            assertTrue(RecipeCategoryBlacklist.clientBlocked(blocked));
            assertTrue(RecipeCategoryBlacklist.clientBlockedFamily(blocked));
            assertTrue(RecipeCategoryBlacklist.serverBlocked(blocked));
            assertTrue(RecipeCategoryBlacklist.serverBlockedFamily(blocked));
        }
        for (String allowed : List.of("minecraft:crafting", "create:mechanical_crafting", "create:mixing",
                "create:packing", "create:automatic_packing", "create:fan_blasting", "create:fan_smoking"))
            assertFalse(RecipeCategoryBlacklist.clientBlocked(allowed), allowed);
        assertFalse(RecipeCategoryBlacklist.clientBlockedFamily("crafting"));
    }

    @Test void emptySyncedListReenablesCategoriesAndUpdatesReplaceTheOldPolicy()
    {
        RecipeCategoryBlacklist.applySynced(RecipeCategoryBlacklist.DEFAULTS);
        RecipeCategoryBlacklist.applySynced(List.of());
        assertFalse(RecipeCategoryBlacklist.clientBlocked("create:automatic_shaped"));
        assertTrue(RecipeCategoryBlacklist.serverBlocked("create:automatic_shaped"), "client policy cannot override the server config");
        RecipeCategoryBlacklist.applySynced(List.of("example:press"));
        assertTrue(RecipeCategoryBlacklist.clientBlocked("example:press"));
        assertFalse(RecipeCategoryBlacklist.clientBlocked("example:pressing"));
        assertFalse(RecipeCategoryBlacklist.clientBlocked("create:automatic_shapeless"));
    }

    @Test void onlyVanillaCategoryAliasesNormalizeToNativeExecutionFamilies()
    {
        assertTrue(RecipeCategoryBlacklist.blocksFamily(List.of("minecraft:crafting"), "crafting"));
        assertTrue(RecipeCategoryBlacklist.blocksFamily(List.of("minecraft:furnace"), "smelting"));
        assertFalse(RecipeCategoryBlacklist.blocksFamily(RecipeCategoryBlacklist.DEFAULTS, "create:mechanical_crafting"));
        assertFalse(RecipeCategoryBlacklist.blocksFamily(RecipeCategoryBlacklist.DEFAULTS, "create:compacting"));
    }

    @Test void bindingSelectionAlsoRejectsBlockedTypesWithoutRemovingTheNativeMachine()
    {
        var selected = java.util.Set.of("create:automatic_shaped", "create:mechanical_crafting");
        assertEquals(java.util.Set.of("create:mechanical_crafting"), VanillaProvisionerRecipeTypes.accepted(selected, selected));
        assertEquals(java.util.Set.of("create:mechanical_crafting"), VanillaProvisionerRecipeTypes.directBindable(selected));
    }

    @Test void rejectsWildcardsInvalidIdsAndOversizedLists()
    {
        assertTrue(RecipeCategoryBlacklist.validList(List.of()));
        assertFalse(RecipeCategoryBlacklist.validList(List.of("create:*")));
        assertFalse(RecipeCategoryBlacklist.validList(List.of("crafting")));
        assertFalse(RecipeCategoryBlacklist.validList(List.of("Create:automatic_shaped")));
        assertFalse(RecipeCategoryBlacklist.validList(java.util.Collections.nCopies(1025, "test:type")));
    }
}
