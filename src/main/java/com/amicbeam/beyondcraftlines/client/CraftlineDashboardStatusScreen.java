package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.menu.DashboardStatusMenu;
import com.amicbeam.beyondcraftlines.common.network.DashboardStatusPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestDashboardStatusPayload;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class CraftlineDashboardStatusScreen extends AbstractContainerScreen<DashboardStatusMenu>
{
    private static final int PANEL = 0xFFF0F0F0;
    private static final int EDGE = 0xFF555B62;
    private static final int SHADOW = 0xFF202A36;
    private static final int ROW_HEIGHT = 42;
    private List<DashboardView> dashboards = List.of();
    private int scrollOffset;
    private int refreshTicks;

    public CraftlineDashboardStatusScreen(DashboardStatusMenu menu, Inventory inventory, Component title)
    { super(menu, inventory, title); }

    @Override protected void init()
    {
        imageWidth = Math.min(460, Math.max(300, width - 20));
        imageHeight = Math.min(320, Math.max(220, height - 20));
        super.init();
        DashboardStatusPayload.clientReceiver = this::receive;
        request();
    }

    @Override protected void containerTick()
    {
        super.containerTick();
        if (++refreshTicks >= CraftlinesConfig.ORDER_STATUS_REFRESH_INTERVAL_TICKS.get())
        { refreshTicks = 0; request(); }
    }

    private void request()
    { PacketDistributor.sendToServer(new RequestDashboardStatusPayload(menu.networkId())); }

    private void receive(net.minecraft.nbt.CompoundTag root)
    {
        List<DashboardView> next = new ArrayList<>();
        var list = root.getList("dashboards", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++)
        {
            var value = list.getCompound(index);
            IStackKey<?> key = decodeKey(value);
            if (key == null || key.isEmpty()) continue;
            next.add(new DashboardView(key, value.getLong("observed"), value.getLong("desired"),
                    value.getString("stock_mode"), value.getString("redstone_mode"),
                    value.getBoolean("automatic_order"), value.getString("error"),
                    value.getString("dimension"), value.getInt("x"), value.getInt("y"), value.getInt("z")));
        }
        dashboards = List.copyOf(next);
        clampScroll();
    }

    private IStackKey<?> decodeKey(net.minecraft.nbt.CompoundTag value)
    {
        if (minecraft.level == null) return null;
        ResourceLocation type = ResourceLocation.tryParse(value.getString("key_type"));
        if (type == null) return null;
        try { return StackKeyRegistry.getType(type).deserializeNBT(
                value.getCompound("key"), minecraft.level.registryAccess()); }
        catch (RuntimeException | LinkageError ignored) { return null; }
    }

    private int listTop() { return topPos + 34; }
    private int listBottom() { return topPos + imageHeight - 10; }
    private int viewportHeight() { return Math.max(1, listBottom() - listTop()); }
    private int contentHeight() { return dashboards.size() * ROW_HEIGHT; }
    private int maxScroll() { return Math.max(0, contentHeight() - viewportHeight()); }
    private void clampScroll() { scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll())); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos + 12, topPos + 12, 0x253545, false);
        Component network = Component.translatable("gui.beyond_craftlines.status.network", menu.networkId());
        graphics.drawString(font, network, leftPos + imageWidth - 12 - font.width(network),
                topPos + 12, 0x687784, false);
        renderDashboards(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderDashboards(GuiGraphics graphics)
    {
        if (dashboards.isEmpty())
        {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.beyond_craftlines.dashboard_status.empty"),
                    leftPos + imageWidth / 2, listTop() + 18, 0x687784);
            return;
        }
        graphics.enableScissor(leftPos + 10, listTop(), leftPos + imageWidth - 10, listBottom());
        int y = listTop() - scrollOffset;
        for (int index = 0; index < dashboards.size(); index++)
        {
            DashboardView dashboard = dashboards.get(index);
            if (y + ROW_HEIGHT <= listTop()) { y += ROW_HEIGHT; continue; }
            if (y >= listBottom()) break;
            graphics.fill(leftPos + 10, y, leftPos + imageWidth - 10, y + ROW_HEIGHT - 2,
                    (index & 1) == 0 ? 0xFFF8F8F8 : 0xFFE8EBEE);
            dashboard.key().getRender().render(graphics, dashboard.key(), leftPos + 18, y + 12);
            Component name = dashboard.key().getRender().getDisplayName(dashboard.key());
            String amount = name.getString() + "  " + dashboard.observed() + "/" + dashboard.desired();
            graphics.drawString(font, font.plainSubstrByWidth(amount, imageWidth - 174),
                    leftPos + 40, y + 7, dashboard.error().isBlank() ? 0x253545 : 0xB23A48, false);
            String location = dashboard.dimension() + "  " + dashboard.x() + "," + dashboard.y() + "," + dashboard.z();
            graphics.drawString(font, font.plainSubstrByWidth(location, 126),
                    leftPos + imageWidth - 142, y + 7, 0x687784, false);
            Component mode = Component.translatable("gui.beyond_craftlines.dashboard.stock_mode." + dashboard.stockMode());
            Component redstone = Component.translatable(
                    "gui.beyond_craftlines.dashboard.redstone_mode." + dashboard.redstoneMode());
            String state = dashboard.error().isBlank()
                    ? Component.translatable(dashboard.automaticOrder()
                    ? "gui.beyond_craftlines.dashboard_status.ordering"
                    : "gui.beyond_craftlines.dashboard_status.idle").getString()
                    : errorText(dashboard.error()).getString();
            String details = mode.getString() + " · " + redstone.getString() + " · " + state;
            graphics.drawString(font, font.plainSubstrByWidth(details, imageWidth - 58),
                    leftPos + 40, y + 23, dashboard.error().isBlank() ? 0x526273 : 0xB23A48, false);
            y += ROW_HEIGHT;
        }
        graphics.flush();
        graphics.disableScissor();
        if (maxScroll() > 0)
        {
            int trackX = leftPos + imageWidth - 14;
            int thumbHeight = Math.max(12, viewportHeight() * viewportHeight() / contentHeight());
            int thumbY = listTop() + scrollOffset * (viewportHeight() - thumbHeight) / maxScroll();
            graphics.fill(trackX, listTop(), trackX + 2, listBottom(), 0xFFCAD0D5);
            graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF009CEB);
        }
    }

    private static Component errorText(String error)
    {
        if (error.equals("dashboard_container_unavailable"))
            return Component.translatable("error.beyond_craftlines.dashboard_container_unavailable");
        if (error.equals("dashboard_container_blocked"))
            return Component.translatable("error.beyond_craftlines.dashboard_container_blocked");
        return Component.literal(error);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (mouseX >= leftPos + 10 && mouseX < leftPos + imageWidth - 10
                && mouseY >= listTop() && mouseY < listBottom() && maxScroll() > 0)
        {
            scrollOffset -= (int) Math.signum(scrollY) * ROW_HEIGHT;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, SHADOW);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.fill(leftPos + 10, topPos + 28, leftPos + imageWidth - 10, topPos + 29, EDGE);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override public void removed()
    { super.removed(); DashboardStatusPayload.clientReceiver = ignored -> {}; }

    private record DashboardView(IStackKey<?> key, long observed, long desired,
                                 String stockMode, String redstoneMode, boolean automaticOrder,
                                 String error, String dimension, int x, int y, int z) {}
}
