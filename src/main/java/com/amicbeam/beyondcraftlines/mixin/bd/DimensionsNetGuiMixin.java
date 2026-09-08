package com.amicbeam.beyondcraftlines.mixin.bd;

import com.amicbeam.beyondcraftlines.BeyondCraftlines;
import com.amicbeam.beyondcraftlines.common.network.OpenDashboardStatusMenuPayload;
import com.amicbeam.beyondcraftlines.common.network.OpenOrderStatusMenuPayload;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.LeftButtonSidebar;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Craftlines entry buttons in BD's sidebar, including when BD rebuilds its widgets. */
@Mixin(value = DimensionsNetGUI.class, remap = false)
public abstract class DimensionsNetGuiMixin extends Screen
{
    @Shadow(remap = false) protected LeftButtonSidebar leftButtonSidebar;

    protected DimensionsNetGuiMixin(Component title)
    {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void beyondCraftlines$addSidebarButtons(CallbackInfo callback)
    {
        IconButton status = new IconButton(0, 0, 16, 16,
                ResourceLocation.fromNamespaceAndPath(
                        BeyondCraftlines.MOD_ID, "widget/crafting_status"),
                ignored -> PacketDistributor.sendToServer(new OpenOrderStatusMenuPayload()));
        status.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.beyond_craftlines.open_crafting_status")));
        addRenderableWidget(leftButtonSidebar.addButton(status));

        IconButton dashboards = new IconButton(0, 0, 16, 16,
                ResourceLocation.fromNamespaceAndPath(
                        BeyondCraftlines.MOD_ID, "widget/crafting_dashboard"),
                ignored -> PacketDistributor.sendToServer(new OpenDashboardStatusMenuPayload()));
        dashboards.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.beyond_craftlines.open_dashboard_status")));
        addRenderableWidget(leftButtonSidebar.addButton(dashboards));
    }
}
