package com.amicbeam.beyondcraftlines.client.integration.jei;

import com.amicbeam.beyondcraftlines.common.crafting.DynamicCraftingFixtures;
import com.amicbeam.beyondcraftlines.common.crafting.RecipeCategoryBlacklist;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class BlacklistedCategoryWarmupTest
{
    @AfterEach void clear()
    {
        JeiCatalystIndex.clear();
        RecipeCategoryBlacklist.clearSynced();
    }

    @Test void persistedBlockedSelectionCannotLeaveInputGroupWarmupWaitingForever()
    {
        RecipeCategoryBlacklist.applySynced(RecipeCategoryBlacklist.DEFAULTS);
        var blocked = Set.of(DynamicCraftingFixtures.id("create:automatic_shaped"));
        JeiCatalystIndex.requestInputGroupsFor(blocked);
        assertTrue(JeiCatalystIndex.inputGroupsReady(blocked));
    }
}
