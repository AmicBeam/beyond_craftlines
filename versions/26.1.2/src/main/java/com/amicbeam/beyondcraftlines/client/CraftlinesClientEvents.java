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
import com.amicbeam.beyondcraftlines.common.network.BindMachinePayload;
import com.amicbeam.beyondcraftlines.common.item.NetworkLinkerItem;
import com.amicbeam.beyondcraftlines.client.tooltip.ClientRecipePreviewTooltip;
import com.amicbeam.beyondcraftlines.client.tooltip.RecipePreviewTooltip;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
            NetworkLinkerItem.CLIENT_BIND_REQUEST = (context, remove) -> {
                var player = context.getPlayer();
                if (player == null) return net.minecraft.world.InteractionResult.PASS;
                boolean connectionEditing = ClientBindingVisuals.isEditingProvisionerConnections();
                var types = connectionEditing ? java.util.Set.<Identifier>of()
                        : com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex.recipeTypesFor(
                        new net.minecraft.world.item.ItemStack(context.getLevel()
                                .getBlockState(context.getClickedPos()).getBlock().asItem()));
                var inputGroups = connectionEditing ? java.util.List.<String>of()
                        : com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry.encode(
                        com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                                .inputGroupsFor(types));
                ClientPacketDistributor.sendToServer(BindMachinePayload.of(context.getClickedPos(), types,
                        context.getClickedFace(), inputGroups, remove));
                return net.minecraft.world.InteractionResult.SUCCESS;
            };
        }

        @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event)
        {
            event.register(CraftlinesMenus.ORDER.get(), CraftlineOrderScreen::new);
            event.register(CraftlinesMenus.STATUS.get(), CraftlineStatusScreen::new);
            event.register(CraftlinesMenus.PROVISIONER.get(), ProvisionerConfigScreen::new);
            event.register(CraftlinesMenus.DASHBOARD.get(), DashboardConfigScreen::new);
            event.register(CraftlinesMenus.DASHBOARD_STATUS.get(), CraftlineDashboardStatusScreen::new);
        }

        @SubscribeEvent public static void registerKeyMappings(RegisterKeyMappingsEvent event)
        {
            CraftlinesKeyMappings.register(event);
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
        private static PendingBoundConfig pendingBoundConfig;

        @SubscribeEvent public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
        {
            ClientBindingVisuals.onLoggingIn(event);
            com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.onLoggingIn();
        }

        @SubscribeEvent public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
        {
            pendingBoundConfig = null;
            ClientBindingVisuals.onLoggingOut(event);
            com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.onLoggingOut();
        }

        @SubscribeEvent public static void recipesReceived(RecipesReceivedEvent event)
        {
            com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.clearRecipeCache();
            com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex.refresh();
        }

        @SubscribeEvent public static void advanceJeiRecipeIndex(ScreenEvent.Render.Post event)
        {
            com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.clientFrame();
        }

        @SubscribeEvent public static void render(RenderLevelStageEvent.AfterTranslucentParticles event)
        {
            ClientBindingVisuals.render(event);
            com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.clientFrame();
            sendPendingBoundConfigIfReady();
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
            pendingBoundConfig = null;
            if (action == LinkerAttackPolicy.Action.VERIFY_WITH_SERVER)
            {
                sendBoundConfig(hit.getBlockPos(), java.util.Set.of());
                return;
            }
            var types = new java.util.LinkedHashSet<>(
                    provisioner ? java.util.Set.<Identifier>of()
                            : com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                            .recipeTypesFor(new ItemStack(state.getBlock().asItem())));
            if (!provisioner && types.isEmpty())
            {
                String vanillaCategory = com.amicbeam.beyondcraftlines.common.crafting
                        .VanillaProvisionerRecipeTypes.categoryForBlock(blockId);
                types.add(vanillaCategory == null ? blockId : Identifier.parse(vanillaCategory));
            }
            if (!types.isEmpty() && !com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                    .inputGroupsReady(types))
            {
                com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                        .requestInputGroupsFor(types);
                pendingBoundConfig = new PendingBoundConfig(hit.getBlockPos().immutable(), Set.copyOf(types));
            }
            else sendBoundConfig(hit.getBlockPos(), types);
            event.setCanceled(action == LinkerAttackPolicy.Action.OPEN_AND_CANCEL_ATTACK);
        }

        private static void sendPendingBoundConfigIfReady()
        {
            PendingBoundConfig pending = pendingBoundConfig;
            if (pending == null || !com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                    .inputGroupsReady(pending.types())) return;
            pendingBoundConfig = null;
            sendBoundConfig(pending.position(), pending.types());
        }

        private static void sendBoundConfig(net.minecraft.core.BlockPos position,
                                            Set<net.minecraft.resources.Identifier> types)
        {
            ClientPacketDistributor.sendToServer(OpenBoundMachineConfigPayload.of(position, types,
                    com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry.encode(
                            com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                                    .inputGroupsFor(types))));
        }

        private record PendingBoundConfig(net.minecraft.core.BlockPos position,
                                          Set<net.minecraft.resources.Identifier> types) {}

        @SubscribeEvent public static void addStatusButton(ScreenEvent.Init.Post event)
        {
            if (!(event.getScreen() instanceof DimensionsNetGUI<?> screen)) return;
            IconButton button = new IconButton(
                    screen.getLeftPos() - 18, screen.getTopPos() + 6 + 18 * 8, 16, 16,
                    Identifier.fromNamespaceAndPath(
                            BeyondCraftlines.MOD_ID, "widget/crafting_status"),
                    ignored -> ClientPacketDistributor.sendToServer(new OpenOrderStatusMenuPayload()));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "tooltip.beyond_craftlines.open_crafting_status")));
            event.addListener(button);
            IconButton dashboards = new IconButton(
                    screen.getLeftPos() - 18, screen.getTopPos() + 6 + 18 * 9, 16, 16,
                    Identifier.fromNamespaceAndPath(
                            BeyondCraftlines.MOD_ID, "widget/crafting_dashboard"),
                    ignored -> ClientPacketDistributor.sendToServer(new OpenDashboardStatusMenuPayload()));
            dashboards.setTooltip(Tooltip.create(Component.translatable(
                    "tooltip.beyond_craftlines.open_dashboard_status")));
            event.addListener(dashboards);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void openOrderFromKey(ScreenEvent.MouseButtonPressed.Pre event)
        {
            if (!CraftlinesKeyMappings.ORDER_HOVERED_RESOURCE.matchesMouse(
                    event.getMouseButtonEvent())) return;
            if (openOrderUnderMouse(event.getScreen(), event.getMouseX(), event.getMouseY()))
                event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void openOrderFromKey(ScreenEvent.KeyPressed.Pre event)
        {
            if (!CraftlinesKeyMappings.ORDER_HOVERED_RESOURCE.matches(event.getKeyEvent())) return;
            Minecraft minecraft = Minecraft.getInstance();
            int screenWidth = minecraft.getWindow().getScreenWidth();
            int screenHeight = minecraft.getWindow().getScreenHeight();
            if (screenWidth <= 0 || screenHeight <= 0) return;
            double mouseX = minecraft.mouseHandler.xpos() * event.getScreen().width / screenWidth;
            double mouseY = minecraft.mouseHandler.ypos() * event.getScreen().height / screenHeight;
            if (openOrderUnderMouse(event.getScreen(), mouseX, mouseY)) event.setCanceled(true);
        }

        private static boolean openOrderUnderMouse(net.minecraft.client.gui.screens.Screen screen,
                                                   double mouseX, double mouseY)
        {
            if (Minecraft.getInstance().player == null
                    || !Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()) return false;
            if (com.amicbeam.beyondcraftlines.client.integration.emi.EmiOptionalIntegration
                    .orderIngredientUnderMouse(mouseX, mouseY)
                    || com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin
                    .orderIngredientUnderMouse())
                return true;
            if (!(screen instanceof DimensionsNetGUI<?> networkScreen)) return false;
            var level = Minecraft.getInstance().level;
            if (level == null) return false;
            for (var slot : networkScreen.getMenu().slots)
            {
                if (!(slot instanceof AbstractStackTypedSlot typed) || !slot.isActive()) continue;
                int x = networkScreen.getLeftPos() + slot.x;
                int y = networkScreen.getTopPos() + slot.y;
                if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) continue;
                if (!(typed.getStack().key() instanceof ItemStackKey itemKey)) return false;
                com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin.orderTarget(
                        new ItemStackKey(itemKey.getReadOnlyStack().copyWithCount(1)));
                return true;
            }
            return false;
        }

    }

    private static void showBindFeedback(String rawType)
    {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        net.minecraft.resources.Identifier type = net.minecraft.resources.Identifier.tryParse(rawType);
        Component title = type == null ? Component.literal(rawType)
                : com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                .recipeTypeTitle(type).orElse(Component.literal(rawType));
        player.sendSystemMessage(Component.translatable(
                "message.beyond_craftlines.machine_bound", rawType, title));
    }
}
