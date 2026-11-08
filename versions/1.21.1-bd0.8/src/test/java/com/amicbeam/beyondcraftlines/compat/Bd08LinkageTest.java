package com.amicbeam.beyondcraftlines.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regression for the 0.8 superclass change that previously stopped mod registration. */
class Bd08LinkageTest
{
    @Test void blockEntitiesLinkAgainstTheNewSuperclass() throws Exception
    {
        ClassLoader loader = getClass().getClassLoader();
        for (String name : new String[]{"CraftlineDashboardBlockEntity", "CraftlineProvisionerBlockEntity"})
        {
            Class<?> type = Class.forName(
                    "com.amicbeam.beyondcraftlines.common.runtime." + name, false, loader);
            assertEquals("com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity",
                    type.getSuperclass().getName());
            // Resolving signatures also catches removed parameter/return types without initializing registries.
            assertTrue(type.getDeclaredMethods().length > 0);
        }
    }

    @Test void registersTheNewPageWithoutTheLegacyGui()
    {
        com.amicbeam.beyondcraftlines.client.bd08.CraftlinesPages.register();
        assertTrue(com.wintercogs.beyonddimensions.api.ui.page.BdPages.INSTANCE.ids().contains(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("beyond_craftlines", "status")));
    }

    @Test void clientEntryPointsLinkWithoutTheRemovedGui() throws Exception
    {
        ClassLoader loader = getClass().getClassLoader();
        for (String name : new String[]{
                "com.amicbeam.beyondcraftlines.client.CraftlinesClientEvents$GameBus",
                "com.amicbeam.beyondcraftlines.client.bd08.CraftlinesPages"})
        {
            Class<?> type = Class.forName(name, false, loader);
            assertTrue(type.getDeclaredMethods().length > 0);
        }
    }
}
