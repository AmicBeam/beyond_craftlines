package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.client.tooltip.ClientRecipePreviewTooltip;
import com.amicbeam.beyondcraftlines.client.tooltip.RecipePreviewTooltip;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Explicit Forge mod-bus wiring; Forge's subscriber annotation defaults to the game bus. */
public final class ForgeClientBootstrap {
    private ForgeClientBootstrap() {}

    public static void register(IEventBus modBus) {
        ClientBindingVisuals.initialize();
        com.amicbeam.beyondcraftlines.common.network.BindMachineFeedbackPayload.clientReceiver =
                CraftlinesClientEvents::showBindFeedback;
        modBus.addListener(ForgeClientBootstrap::setup);
        modBus.addListener(ForgeClientBootstrap::registerTooltipComponents);
        modBus.addListener(ForgeClientBootstrap::registerRenderers);
        modBus.addListener(ForgeClientBootstrap::modifyModels);
        modBus.addListener(ForgeClientBootstrap::configReloaded);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(CraftlinesMenus.ORDER.get(), CraftlineOrderScreen::new);
            MenuScreens.register(CraftlinesMenus.STATUS.get(), CraftlineStatusScreen::new);
            MenuScreens.register(CraftlinesMenus.PROVISIONER.get(), ProvisionerConfigScreen::new);
            MenuScreens.register(CraftlinesMenus.DASHBOARD.get(), DashboardConfigScreen::new);
            MenuScreens.register(CraftlinesMenus.DASHBOARD_STATUS.get(), CraftlineDashboardStatusScreen::new);
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                    CraftlinesBlockEntities.CRAFTLINE_DASHBOARD.get(), CraftlineDashboardRenderer::new);
        });
    }
    private static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(RecipePreviewTooltip.class, ClientRecipePreviewTooltip::new);
    }
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CraftlinesBlockEntities.CRAFTLINE_PROVISIONER.get(),
                ProvisionerFallbackLabelRenderer::new);
    }
    private static void modifyModels(ModelEvent.ModifyBakingResult event) { ProvisionerMaterialModel.install(event); }
    private static void configReloaded(ModConfigEvent.Reloading event) {
        if (!CraftlinesConfig.isClientConfig(event.getConfig())) return;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> { if (minecraft.level != null) minecraft.levelRenderer.allChanged(); });
    }
}
