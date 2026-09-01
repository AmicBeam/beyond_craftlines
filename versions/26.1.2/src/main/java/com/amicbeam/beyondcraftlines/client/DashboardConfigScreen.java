package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.dashboard.DashboardRedstoneMode;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardConfigStatus;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardStockMode;
import com.amicbeam.beyondcraftlines.common.menu.DashboardConfigMenu;
import com.amicbeam.beyondcraftlines.common.network.ConfigureDashboardPayload;
import com.amicbeam.beyondcraftlines.common.network.OpenDashboardRecipePayload;
import com.amicbeam.beyondcraftlines.common.runtime.CraftlineDashboardBlockEntity;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class DashboardConfigScreen extends AbstractContainerScreen<DashboardConfigMenu>
{
    private static final int PANEL_HEIGHT = 225;
    private static final int MODE_BUTTON_Y = 54;
    private static final int RECIPE_BUTTON_Y = 80;
    private static final int OBSERVED_LABEL_Y = 106;
    private static final int BLOCKING_LABEL_Y = 117;
    private static final int INVENTORY_LABEL_Y = 132;

    private IStackKey<?> target;
    private DashboardStockMode stockMode;
    private DashboardRedstoneMode redstoneMode;
    private boolean recipeConfigured;
    private EditBox amount;
    private Button stockButton;
    private Button redstoneButton;
    private Button recipeButton;

    public DashboardConfigScreen(DashboardConfigMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, 176, PANEL_HEIGHT);
        target = menu.target();
        stockMode = menu.stockMode();
        redstoneMode = menu.redstoneMode();
        recipeConfigured = menu.recipeConfigured();
    }

    public Rect2i ghostTargetArea()
    { return new Rect2i(leftPos + DashboardConfigMenu.SAMPLE_SLOT_X,
            topPos + DashboardConfigMenu.SAMPLE_SLOT_Y, 18, 18); }

    public void setGhostTarget(IStackKey<?> value)
    {
        if (value == null || value.isEmpty()) return;
        boolean targetChanged = target == null || target.isEmpty()
                || !com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch.exact(target, value);
        recipeConfigured = DashboardConfigStatus.recipeConfiguredAfterTargetChange(
                recipeConfigured, targetChanged);
        menu.setGhostTarget(value);
        target = menu.target();
        updateLabels();
        sendConfiguration();
    }

    @Override protected void init()
    {
        super.init();
        amount = new EditBox(font, leftPos + 34, topPos + 30, 134, 18,
                Component.translatable("gui.beyond_craftlines.amount"));
        amount.setValue(Long.toString(menu.desired()));
        amount.setFilter(value -> value.matches("[0-9]{0,19}")
                && (value.isEmpty() || positive(value)));
        amount.setResponder(ignored -> updateLabels());
        addRenderableWidget(amount);
        stockButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            stockMode = stockMode.next();
            updateLabels();
            sendConfiguration();
        }).bounds(leftPos + 8, topPos + MODE_BUTTON_Y, 78, 20).build());
        redstoneButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            redstoneMode = redstoneMode.next();
            updateLabels();
            sendConfiguration();
        }).bounds(leftPos + 90, topPos + MODE_BUTTON_Y, 78, 20).build());
        recipeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> openRecipeTree())
                .bounds(leftPos + 8, topPos + RECIPE_BUTTON_Y, 160, 20).build());
        updateLabels();
    }

    private void updateLabels()
    {
        stockButton.setMessage(Component.translatable(
                "gui.beyond_craftlines.dashboard.stock_mode." + stockMode.id()));
        redstoneButton.setMessage(Component.translatable(
                "gui.beyond_craftlines.dashboard.redstone_mode." + redstoneMode.id()));
        recipeButton.setMessage(Component.translatable(recipeConfigured
                ? "gui.beyond_craftlines.dashboard.edit_recipe"
                : "gui.beyond_craftlines.dashboard.set_recipe"));
        recipeButton.active = target != null && !target.isEmpty() && parsedAmount() > 0;
    }

    private void openRecipeTree()
    {
        if (target == null || target.isEmpty() || parsedAmount() < 1) return;
        sendConfiguration();
        ClientPacketDistributor.sendToServer(new OpenDashboardRecipePayload(menu.position()));
    }

    private void sendConfiguration()
    {
        long desired = parsedAmount();
        if (desired < 1 || target == null) return;
        ClientPacketDistributor.sendToServer(new ConfigureDashboardPayload(
                menu.position(), target, desired, stockMode.id(), redstoneMode.id()));
    }

    @Override public void onClose()
    {
        sendConfiguration();
        super.onClose();
    }

    private long parsedAmount()
    {
        try { return Long.parseLong(amount == null || amount.getValue().isBlank()
                ? "0" : amount.getValue()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static boolean positive(String value)
    {
        try { return Long.parseLong(value) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    private void syncTargetFromMenu()
    {
        IStackKey<?> next = menu.target();
        boolean targetChanged = target == null || target.isEmpty() != next.isEmpty()
                || !target.isEmpty() && !com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                .exact(target, next);
        if (!targetChanged) return;
        recipeConfigured = DashboardConfigStatus.recipeConfiguredAfterTargetChange(
                recipeConfigured, true);
        target = next;
        updateLabels();
        sendConfiguration();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        double mouseX = event.x();
        double mouseY = event.y();
        if (amount != null && amount.isFocused() && !amount.isMouseOver(mouseX, mouseY))
        {
            amount.setFocused(false);
            setFocused(null);
            sendConfiguration();
        }
        if (mouseX >= leftPos + DashboardConfigMenu.SAMPLE_SLOT_X
                && mouseX < leftPos + DashboardConfigMenu.SAMPLE_SLOT_X + 18
                && mouseY >= topPos + DashboardConfigMenu.SAMPLE_SLOT_Y
                && mouseY < topPos + DashboardConfigMenu.SAMPLE_SLOT_Y + 18)
        {
            if (event.button() == 1)
            {
                menu.setGhostTarget(ItemStackKey.EMPTY);
                target = menu.target();
                recipeConfigured = false;
                updateLabels();
                sendConfiguration();
                return true;
            }
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty())
            {
                setGhostTarget(new ItemStackKey(carried.copyWithCount(1)));
                return true;
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        syncTargetFromMenu();
        return handled;
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics,
                                            int mouseX, int mouseY, float partialTick)
    {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth,
                topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth,
                topPos + imageHeight, 0xFF555555);
        drawSlot(graphics, leftPos + DashboardConfigMenu.SAMPLE_SLOT_X,
                topPos + DashboardConfigMenu.SAMPLE_SLOT_Y, 18);
        for (int index = 1; index < menu.slots.size(); index++)
        {
            var slot = menu.slots.get(index);
            drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, 18);
        }
        if (target != null && !target.isEmpty() && !(target instanceof ItemStackKey))
            target.getRender().render(graphics, target,
                    leftPos + DashboardConfigMenu.SAMPLE_SLOT_X + 1,
                    topPos + DashboardConfigMenu.SAMPLE_SLOT_Y + 1);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, int size)
    {
        graphics.fill(x, y, x + size, y + size, 0xFF555555);
        graphics.fill(x + 1, y + 1, x + size, y + size, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF8B8B8B);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        graphics.text(font, title, 8, 9, 0xFF404040, false);
        String currentError = currentError();
        if (currentError.isBlank())
            graphics.text(font, Component.translatable(
                    "gui.beyond_craftlines.dashboard.observed", menu.observed()),
                    8, OBSERVED_LABEL_Y, 0xFF404040, false);
        else
        {
            Component error = currentError.equals(DashboardConfigStatus.RECIPE_UNCONFIGURED)
                    ? Component.translatable("gui.beyond_craftlines.dashboard.recipe_unconfigured")
                    : currentError.equals("dashboard_container_unavailable")
                            ? Component.translatable("error.beyond_craftlines.dashboard_container_unavailable")
                    : currentError.equals("dashboard_container_blocked")
                            ? Component.translatable("error.beyond_craftlines.dashboard_container_blocked")
                            : Component.literal(currentError);
            graphics.text(font, font.plainSubstrByWidth(error.getString(), 160),
                    8, OBSERVED_LABEL_Y, 0xFFAA2020, false);
        }
        String blocking = Component.translatable("gui.beyond_craftlines.blocking_mode_"
                + (menu.blocking() ? "on" : "off")).getString();
        graphics.text(font, Component.translatable(
                "gui.beyond_craftlines.dashboard.saved_blocking", blocking),
                8, BLOCKING_LABEL_Y, 0xFF404040, false);
        graphics.text(font, Component.translatable("container.inventory"),
                7, INVENTORY_LABEL_Y, 0xFF404040, false);
    }

    private String currentError()
    {
        String runtimeError;
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.position()) instanceof CraftlineDashboardBlockEntity dashboard)
            runtimeError = dashboard.lastError();
        else runtimeError = menu.error();
        return DashboardConfigStatus.visibleError(recipeConfigured, runtimeError);
    }
}
