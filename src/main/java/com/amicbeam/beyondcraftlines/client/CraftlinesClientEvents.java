package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderStatusMenuPayload;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderMenuPayload;
import com.amicbeam.beyondcraftlines.client.tooltip.ClientRecipePreviewTooltip;
import com.amicbeam.beyondcraftlines.client.tooltip.RecipePreviewTooltip;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

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
        { ClientBindingVisuals.onLoggingIn(event); }

        @SubscribeEvent public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
        { ClientBindingVisuals.onLoggingOut(event); }

        @SubscribeEvent public static void render(RenderLevelStageEvent event)
        { ClientBindingVisuals.render(event); }

        @SubscribeEvent public static void recipesUpdated(RecipesUpdatedEvent event)
        { com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.clearRecipeCache(); }

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
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void openRecipeTreeBeforeBdTransfer(ScreenEvent.MouseButtonPressed.Pre event)
        {
            if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                    || !(event.getScreen() instanceof DimensionsNetGUI<?> screen)
                    || !screen.getMenu().getCarried().isEmpty()) return;
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
                var target = BuiltInRegistries.ITEM.getKey(itemKey.getSource());
                boolean hasRecipe = com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService
                        .visibleRecipes(level).stream().anyMatch(holder -> BuiltInRegistries.ITEM.getKey(holder.value()
                                .getResultItem(level.registryAccess()).getItem()).equals(target));
                if (!hasRecipe) return;
                PacketDistributor.sendToServer(new OpenOrderMenuPayload(
                        new ItemStackKey(itemKey.getReadOnlyStack().copyWithCount(1)), "", ""));
                event.setCanceled(true);
                return;
            }
        }
    }

    private static void showBindFeedback(String rawType)
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
