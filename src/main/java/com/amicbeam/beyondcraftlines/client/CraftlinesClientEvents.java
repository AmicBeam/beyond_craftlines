package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesBlockEntities;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesItems;
import com.amicbeam.beyondcraftlines.common.network.OpenBoundMachineConfigPayload;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderStatusMenuPayload;
import com.amicbeam.beyondcraftlines.common.network.OpenDashboardStatusMenuPayload;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload;
import com.amicbeam.beyondcraftlines.client.tooltip.ClientRecipePreviewTooltip;
import com.amicbeam.beyondcraftlines.client.tooltip.RecipePreviewTooltip;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public final class CraftlinesClientEvents
{
    private CraftlinesClientEvents() {}

    @EventBusSubscriber(modid = BeyondCraftlines.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus
    {
        static
        {
            ClientBindingVisuals.initialize();
            com.amicbeam.beyondcraftlines.common.network.BindMachineFeedbackPayload.clientReceiver =
                    CraftlinesClientEvents::showBindFeedback;
        }

        @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event)
        {
            event.register(CraftlinesMenus.ORDER.get(), CraftlineOrderScreen::new);
            event.register(CraftlinesMenus.STATUS.get(), CraftlineStatusScreen::new);
            event.register(CraftlinesMenus.PROVISIONER.get(), ProvisionerConfigScreen::new);
            event.register(CraftlinesMenus.DASHBOARD.get(), DashboardConfigScreen::new);
            event.register(CraftlinesMenus.DASHBOARD_STATUS.get(), CraftlineDashboardStatusScreen::new);
        }

        @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
        {
            event.registerBlockEntityRenderer(CraftlinesBlockEntities.CRAFTLINE_PROVISIONER.get(),
                    ProvisionerFallbackLabelRenderer::new);
            event.registerBlockEntityRenderer(CraftlinesBlockEntities.CRAFTLINE_DASHBOARD.get(),
                    CraftlineDashboardRenderer::new);
        }

        @SubscribeEvent public static void registerTooltipComponents(
                RegisterClientTooltipComponentFactoriesEvent event)
        {
            event.register(RecipePreviewTooltip.class, ClientRecipePreviewTooltip::new);
        }

        @SubscribeEvent public static void modifyModels(ModelEvent.ModifyBakingResult event)
        {
            ProvisionerMaterialModel.install(event);
        }

        @SubscribeEvent public static void configReloaded(ModConfigEvent.Reloading event)
        {
            if (!CraftlinesConfig.isClientConfig(event.getConfig())) return;
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (minecraft.level != null) minecraft.levelRenderer.allChanged();
            });
        }
    }

    @EventBusSubscriber(modid = BeyondCraftlines.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus
    {
        @SubscribeEvent public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
        {
            ClientBindingVisuals.onLoggingIn(event);
            com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.onLoggingIn();
        }

        @SubscribeEvent public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
        {
            ClientBindingVisuals.onLoggingOut(event);
            com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.onLoggingOut();
        }

        @SubscribeEvent public static void render(RenderLevelStageEvent event)
        {
            ClientBindingVisuals.render(event);
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES)
            {
                com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.clientFrame();
            }
        }

        @SubscribeEvent public static void recipesUpdated(RecipesUpdatedEvent event)
        {
            com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.clearRecipeCache();
            com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex.refresh();
        }

        @SubscribeEvent public static void advanceJeiRecipeIndex(ScreenEvent.Render.Post event)
        {
            com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.clientFrame();
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void openBoundMachineConfig(InputEvent.InteractionKeyMappingTriggered event)
        {
            if (!event.isAttack()) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null
                    || !(minecraft.hitResult instanceof BlockHitResult hit)
                    || hit.getType() != HitResult.Type.BLOCK
                    || (!minecraft.player.getMainHandItem().is(CraftlinesItems.NETWORK_LINKER.get())
                    && !minecraft.player.getOffhandItem().is(CraftlinesItems.NETWORK_LINKER.get()))) return;
            var state = minecraft.level.getBlockState(hit.getBlockPos());
            var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            boolean provisioner = state.is(com.amicbeam.beyondcraftlines.common.init.CraftlinesBlocks
                    .CRAFTLINE_PROVISIONER.get());
            boolean knownBound = !provisioner && ClientBindingVisuals.isBoundMachine(hit.getBlockPos(), blockId);
            LinkerAttackPolicy.Action action = LinkerAttackPolicy.decide(
                    provisioner, knownBound, ClientBindingVisuals.bindingSnapshotReady());
            if (action == LinkerAttackPolicy.Action.IGNORE) return;
            if (action == LinkerAttackPolicy.Action.VERIFY_WITH_SERVER)
            {
                sendBoundConfig(hit.getBlockPos(), java.util.Set.of());
                return;
            }
            var types = new java.util.LinkedHashSet<>(
                    provisioner ? java.util.Set.<ResourceLocation>of()
                            : com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                            .recipeTypesFor(new ItemStack(state.getBlock().asItem())));
            if (!provisioner && types.isEmpty()) types.add(blockId);
            if (!types.isEmpty() && !com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                    .inputGroupsReady(types))
                com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                        .requestInputGroupsFor(types);
            sendBoundConfig(hit.getBlockPos(), types);
            event.setCanceled(action == LinkerAttackPolicy.Action.OPEN_AND_CANCEL_ATTACK);
        }

        private static void sendBoundConfig(net.minecraft.core.BlockPos position, Set<ResourceLocation> types)
        {
            PacketDistributor.sendToServer(OpenBoundMachineConfigPayload.of(position, types,
                    com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry.encode(
                            com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                                    .inputGroupsFor(types))));
        }

        @SubscribeEvent public static void addStatusButton(ScreenEvent.Init.Post event)
        {
            if (!(event.getScreen() instanceof DimensionsNetGUI<?> screen)) return;
            IconButton button = new IconButton(
                    screen.getGuiLeft() - 18, screen.getGuiTop() + 6 + 18 * 8, 16, 16,
                    ResourceLocation.fromNamespaceAndPath(
                            BeyondCraftlines.MOD_ID, "widget/crafting_status"),
                    ignored -> PacketDistributor.sendToServer(new OpenOrderStatusMenuPayload()));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "tooltip.beyond_craftlines.open_crafting_status")));
            event.addListener(button);
            IconButton dashboards = new IconButton(
                    screen.getGuiLeft() - 18, screen.getGuiTop() + 6 + 18 * 9, 16, 16,
                    ResourceLocation.fromNamespaceAndPath(
                            BeyondCraftlines.MOD_ID, "widget/crafting_dashboard"),
                    ignored -> PacketDistributor.sendToServer(new OpenDashboardStatusMenuPayload()));
            dashboards.setTooltip(Tooltip.create(Component.translatable(
                    "tooltip.beyond_craftlines.open_dashboard_status")));
            event.addListener(dashboards);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void openOrderFromMiddleClick(ScreenEvent.MouseButtonPressed.Pre event)
        {
            if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                    || Minecraft.getInstance().player == null
                    || !Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) return;
            if (com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin
                    .orderIngredientUnderMouse())
            {
                event.setCanceled(true);
                return;
            }
            if (!(event.getScreen() instanceof DimensionsNetGUI<?> screen)) return;
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            for (var slot : screen.getMenu().slots)
            {
                if (!(slot instanceof AbstractStackTypedSlot typed) || !slot.isActive()) continue;
                int x = screen.getGuiLeft() + slot.x;
                int y = screen.getGuiTop() + slot.y;
                if (event.getMouseX() < x || event.getMouseX() >= x + 16
                        || event.getMouseY() < y || event.getMouseY() >= y + 16) continue;
                if (!(typed.getStack().key() instanceof ItemStackKey itemKey)) return;
                com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.orderTarget(
                        new ItemStackKey(itemKey.getReadOnlyStack().copyWithCount(1)));
                event.setCanceled(true);
                return;
            }
        }

    }

    public static void showBindFeedback(String rawType)
    {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        Component title = type == null ? Component.literal(rawType)
                : com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                .recipeTypeTitle(type).orElse(Component.literal(rawType));
        player.displayClientMessage(Component.translatable(
                "message.beyond_craftlines.machine_bound", rawType, title), false);
    }
}
