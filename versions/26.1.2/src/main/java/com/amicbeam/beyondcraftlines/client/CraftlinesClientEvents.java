package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.init.CraftlinesMenus;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderStatusMenuPayload;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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
            NetworkLinkerItem.CLIENT_BIND_REQUEST = (context, remove) -> {
                var player = context.getPlayer();
                if (player == null) return net.minecraft.world.InteractionResult.PASS;
                var catalyst = new net.minecraft.world.item.ItemStack(
                        context.getLevel().getBlockState(context.getClickedPos()).getBlock().asItem());
                var types = com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex
                        .recipeTypesFor(catalyst);
                if (!remove && types.isEmpty())
                {
                    player.sendSystemMessage(Component.translatable(
                            "error.beyond_craftlines.machine_recipe_type_unknown"));
                    return net.minecraft.world.InteractionResult.FAIL;
                }
                ClientPacketDistributor.sendToServer(BindMachinePayload.of(context.getClickedPos(), types, remove));
                return net.minecraft.world.InteractionResult.SUCCESS;
            };
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

        @SubscribeEvent public static void render(RenderLevelStageEvent.AfterTranslucentParticles event)
        { ClientBindingVisuals.render(event); }

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
                int x = screen.getLeftPos() + slot.x;
                int y = screen.getTopPos() + slot.y;
                if (event.getMouseX() < x || event.getMouseX() >= x + 16
                        || event.getMouseY() < y || event.getMouseY() >= y + 16) continue;
                if (!(typed.getStack().key() instanceof ItemStackKey itemKey)) return;
                var target = BuiltInRegistries.ITEM.getKey(itemKey.getSource());
                boolean hasRecipe = com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService
                        .visibleRecipes(level).stream().anyMatch(holder ->
                                com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver
                                        .outputs(holder.value(), level).stream().anyMatch(output ->
                                                output.key() instanceof ItemStackKey item
                                                        && BuiltInRegistries.ITEM.getKey(item.getSource()).equals(target)));
                if (!hasRecipe) return;
                ClientPacketDistributor.sendToServer(new OpenOrderMenuPayload(
                        new ItemStackKey(itemKey.getReadOnlyStack().copyWithCount(1)), "", ""));
                event.setCanceled(true);
                return;
            }
        }
    }
}
