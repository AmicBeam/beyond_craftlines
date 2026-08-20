package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;

public final class CraftlinesClientEvents
{
    private CraftlinesClientEvents() {}

    @EventBusSubscriber(modid = BeyondCraftlines.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus
    {
        static { ClientBindingVisuals.initialize(); }

        @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event)
        {
            event.register(CraftlinesMenus.ORDER.get(), CraftlineOrderScreen::new);
            event.register(CraftlinesMenus.PROVISIONER.get(), ProvisionerConfigScreen::new);
        }
    }

    @EventBusSubscriber(modid = BeyondCraftlines.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus
    {
        @SubscribeEvent public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
        { ClientBindingVisuals.onLoggingIn(event); }

        @SubscribeEvent public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
        { ClientBindingVisuals.onLoggingOut(event); }

        @SubscribeEvent public static void render(RenderLevelStageEvent event)
        { ClientBindingVisuals.render(event); }

        @SubscribeEvent public static void recipesUpdated(RecipesUpdatedEvent event)
        { com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.clearRecipeCache(); }
    }
}
