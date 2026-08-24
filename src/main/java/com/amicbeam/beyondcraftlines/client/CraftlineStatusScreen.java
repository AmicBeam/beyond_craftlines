package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineStatusMenu;
import com.amicbeam.beyondcraftlines.common.localization.OrderStatusMessage;
import com.amicbeam.beyondcraftlines.common.network.CancelOrderPayload;
import com.amicbeam.beyondcraftlines.common.network.OrderStatusPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestOrderStatusPayload;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CraftlineStatusScreen extends AbstractContainerScreen<CraftlineStatusMenu>
{
    private static final int PANEL = 0xFFF0F0F0;
    private static final int EDGE = 0xFF555B62;
    private static final int SHADOW = 0xFF202A36;
    private static final int ORDER_HEIGHT = 34;
    private static final int STEP_HEIGHT = 24;

    private List<OrderView> orders = List.of();
    private UUID selectedOrder;
    private final Set<UUID> collapsedOrders = new HashSet<>();
    private boolean defaultExpansionApplied;
    private int scrollOffset;
    private int statusTicks;
    private UUID statusSession = UUID.randomUUID();
    private Button cancelButton;

    public CraftlineStatusScreen(CraftlineStatusMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        CraftlineStatusMenu.InitialOrder initial = menu.initialOrder();
        if (initial != null)
        {
            orders = List.of(new OrderView(initial.id(), initial.target(), initial.requested(), 0, 0,
                    initial.blockingMode(), "QUEUED", "", 0, List.of()));
            selectedOrder = initial.id();
        }
    }

    @Override
    protected void init()
    {
        imageWidth = Math.min(460, Math.max(300, width - 20));
        imageHeight = Math.min(320, Math.max(220, height - 20));
        super.init();
        cancelButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.beyond_craftlines.cancel_selected"), ignored -> cancelSelected())
                .bounds(leftPos + imageWidth - 130, topPos + imageHeight - 30, 120, 20).build());
        cancelButton.setTooltip(Tooltip.create(Component.translatable("tooltip.beyond_craftlines.cancel")));
        cancelButton.active = selectedActive();
        OrderStatusPayload.clientReceiver = this::receiveOrders;
        requestStatus();
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        if (++statusTicks >= CraftlinesConfig.ORDER_STATUS_REFRESH_INTERVAL_TICKS.get())
        {
            statusTicks = 0;
            requestStatus();
        }
    }

    private void requestStatus()
    { PacketDistributor.sendToServer(new RequestOrderStatusPayload(menu.networkId(), statusSession)); }

    private void receiveOrders(net.minecraft.nbt.CompoundTag root)
    {
        if (!root.hasUUID("session") || !statusSession.equals(root.getUUID("session"))) return;
        Map<UUID, OrderView> cached = new LinkedHashMap<>();
        if (!root.getBoolean("reset")) orders.forEach(order -> cached.put(order.id(), order));

        var updates = root.getList("updates", Tag.TAG_COMPOUND);
        for (int i = 0; i < updates.size(); i++)
        {
            var value = updates.getCompound(i);
            UUID id = value.getUUID("id");
            OrderView previous = cached.get(id);
            List<StepView> steps = value.getBoolean("reset_steps") || previous == null
                    ? new ArrayList<>() : new ArrayList<>(previous.steps());
            var encodedSteps = value.getList("step_updates", Tag.TAG_COMPOUND);
            for (int stepIndex = 0; stepIndex < encodedSteps.size(); stepIndex++)
            {
                var encoded = encodedSteps.getCompound(stepIndex);
                IStackKey<?> key = decodeKey(encoded);
                if (key == null || key.isEmpty()) continue;
                int existing = -1;
                for (int at = 0; at < steps.size(); at++)
                    if (key.isSame(steps.get(at).key())) { existing = at; break; }
                if (encoded.getBoolean("removed"))
                {
                    if (existing >= 0) steps.remove(existing);
                }
                else
                {
                    StepView step = new StepView(key, encoded.getLong("completed"),
                            encoded.getLong("required"), encoded.getString("fallback"));
                    if (existing >= 0) steps.set(existing, step); else steps.add(step);
                }
            }
            cached.put(id, new OrderView(id, value.getString("target"), value.getLong("requested"),
                    value.getInt("next"), value.getInt("total"), value.getBoolean("blocking_mode"),
                    value.getString("status"), value.getString("message"), value.getLong("revision"),
                    List.copyOf(steps)));
        }

        List<OrderView> next = new ArrayList<>();
        var orderIndex = root.getList("index", Tag.TAG_COMPOUND);
        for (int i = 0; i < orderIndex.size(); i++)
        {
            var indexed = orderIndex.getCompound(i);
            OrderView order = cached.get(indexed.getUUID("id"));
            if (order == null || order.revision() != indexed.getLong("revision"))
            {
                statusSession = UUID.randomUUID();
                requestStatus();
                return;
            }
            next.add(order);
        }
        orders = List.copyOf(next);
        if (!defaultExpansionApplied)
        {
            for (int index = 1; index < orders.size(); index++)
                collapsedOrders.add(orders.get(index).id());
            defaultExpansionApplied = true;
        }
        if (selectedOrder == null || orders.stream().noneMatch(order -> order.id().equals(selectedOrder)))
            selectedOrder = orders.stream().filter(OrderView::active).findFirst()
                    .or(() -> orders.stream().findFirst()).map(OrderView::id).orElse(null);
        clampScroll();
        if (cancelButton != null) cancelButton.active = selectedActive();
    }

    private boolean selectedActive()
    { return orders.stream().anyMatch(order -> order.id().equals(selectedOrder) && order.active()); }

    private void cancelSelected()
    {
        orders.stream().filter(order -> order.id().equals(selectedOrder) && order.active()).findFirst()
                .ifPresent(order -> {
                    PacketDistributor.sendToServer(new CancelOrderPayload(order.id()));
                    requestStatus();
                });
    }

    private int listTop() { return topPos + 36; }
    private int listBottom() { return topPos + imageHeight - 40; }
    private int viewportHeight() { return listBottom() - listTop(); }
    private int orderHeight(OrderView order)
    { return ORDER_HEIGHT + (collapsedOrders.contains(order.id()) ? 0 : order.steps().size() * STEP_HEIGHT); }
    private int contentHeight()
    { return orders.stream().mapToInt(this::orderHeight).sum(); }
    private int maxScroll() { return Math.max(0, contentHeight() - viewportHeight()); }
    private void clampScroll() { scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll())); }

    private IStackKey<?> decodeKey(net.minecraft.nbt.CompoundTag encoded)
    {
        ResourceLocation type = ResourceLocation.tryParse(encoded.getString("key_type"));
        if (type == null || minecraft.level == null) return null;
        try
        { return StackKeyRegistry.getType(type).deserializeNBT(encoded.getCompound("key"), minecraft.level.registryAccess()); }
        catch (RuntimeException | LinkageError ignored) { return null; }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos + 12, topPos + 12, 0x253545, false);
        graphics.drawString(font, Component.translatable("gui.beyond_craftlines.status.network", menu.networkId()),
                leftPos + imageWidth - 12 - font.width(Component.translatable(
                        "gui.beyond_craftlines.status.network", menu.networkId())), topPos + 12, 0x687784, false);
        renderOrders(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderOrders(GuiGraphics graphics)
    {
        if (orders.isEmpty())
        {
            graphics.drawCenteredString(font, Component.translatable("gui.beyond_craftlines.status.empty"),
                    leftPos + imageWidth / 2, listTop() + 18, 0x687784);
            return;
        }
        graphics.enableScissor(leftPos + 10, listTop(), leftPos + imageWidth - 10, listBottom());
        int y = listTop() - scrollOffset;
        for (int index = 0; index < orders.size(); index++)
        {
            OrderView order = orders.get(index);
            int fullHeight = orderHeight(order);
            if (y + fullHeight <= listTop()) { y += fullHeight; continue; }
            if (y >= listBottom()) break;
            boolean selected = order.id().equals(selectedOrder);
            graphics.fill(leftPos + 10, y, leftPos + imageWidth - 10, y + ORDER_HEIGHT - 2,
                    selected ? 0xFFDCEEFF : (index & 1) == 0 ? 0xFFF8F8F8 : 0xFFE8EBEE);
            if (selected) graphics.fill(leftPos + 10, y, leftPos + 13, y + ORDER_HEIGHT - 2, 0xFF009CEB);

            boolean expanded = !collapsedOrders.contains(order.id()) && !order.steps().isEmpty();
            graphics.drawString(font, expanded ? "-" : "+", leftPos + 17, y + 12,
                    order.steps().isEmpty() ? 0xFF9AA4AD : 0xFF526273, false);

            ResourceLocation targetId = ResourceLocation.tryParse(order.target());
            ItemStack target = targetId == null ? ItemStack.EMPTY
                    : new ItemStack(BuiltInRegistries.ITEM.get(targetId));
            if (!target.isEmpty()) graphics.renderItem(target, leftPos + 28, y + 8);
            int textX = leftPos + 50;
            String targetName = target.isEmpty() ? order.target() : target.getHoverName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(targetName + " ×" + order.requested(),
                    imageWidth - 180), textX, y + 6, statusColor(order.status()), false);
            Component status = Component.translatable("gui.beyond_craftlines.status."
                    + order.status().toLowerCase(Locale.ROOT));
            String mode = order.blockingMode() ? " [B]" : "";
            String progress = order.total() == 0 ? status.getString()
                    : status.getString() + " " + order.next() + "/" + order.total();
            graphics.drawString(font, font.plainSubstrByWidth(progress + mode, 118),
                    leftPos + imageWidth - 138, y + 6, statusColor(order.status()), false);
            Component message = orderMessage(order.message());
            if (!message.getString().isBlank()) graphics.drawString(font,
                    font.plainSubstrByWidth(message.getString(), imageWidth - 74), textX, y + 19, 0x6B7580, false);

            y += ORDER_HEIGHT;
            if (expanded)
            {
                for (StepView step : order.steps())
                {
                    graphics.fill(leftPos + 22, y, leftPos + imageWidth - 10, y + STEP_HEIGHT - 1,
                            (index & 1) == 0 ? 0xFFF2F5F7 : 0xFFE1E6EA);
                    graphics.fill(leftPos + 27, y, leftPos + 28, y + STEP_HEIGHT / 2, 0xFF9AA8B4);
                    graphics.fill(leftPos + 27, y + STEP_HEIGHT / 2, leftPos + 34, y + STEP_HEIGHT / 2 + 1,
                            0xFF9AA8B4);
                    step.key().getRender().render(graphics, step.key(), leftPos + 36, y + 4);
                    Component name = step.key().getRender().getDisplayName(step.key());
                    String stepProgress = name.getString() + "  " + step.completed() + "/" + step.required();
                    graphics.drawString(font, font.plainSubstrByWidth(stepProgress, imageWidth - 104),
                            leftPos + 58, y + 8, 0x465563, false);
                    y += STEP_HEIGHT;
                }
            }
        }
        // Item/resource renderers batch their vertices; flush while the list scissor is still active.
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

    private static int statusColor(String status)
    {
        return switch (status)
        {
            case "ERROR", "CANCELLED" -> 0xB23A48;
            case "COMPLETE" -> 0x257A55;
            case "PAUSED" -> 0xB26A00;
            default -> 0x253545;
        };
    }

    private static Component orderMessage(String stored)
    {
        OrderStatusMessage.Decoded decoded = OrderStatusMessage.decode(stored);
        return decoded.isEmpty() ? Component.empty() : Component.translatable(
                decoded.translationKey(), decoded.arguments().toArray());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && mouseX >= leftPos + 10 && mouseX < leftPos + imageWidth - 10
                && mouseY >= listTop() && mouseY < listBottom())
        {
            int y = listTop() - scrollOffset;
            for (OrderView order : orders)
            {
                if (mouseY >= y && mouseY < y + ORDER_HEIGHT)
                {
                    selectedOrder = order.id();
                    if (!order.steps().isEmpty())
                    {
                        if (!collapsedOrders.add(order.id())) collapsedOrders.remove(order.id());
                        clampScroll();
                    }
                    cancelButton.active = selectedActive();
                    return true;
                }
                y += orderHeight(order);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (mouseX >= leftPos + 10 && mouseX < leftPos + imageWidth - 10
                && mouseY >= listTop() && mouseY < listBottom() && maxScroll() > 0)
        {
            scrollOffset -= (int) Math.signum(scrollY) * STEP_HEIGHT;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, SHADOW);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.fill(leftPos + 10, topPos + 28, leftPos + imageWidth - 10, topPos + 29, EDGE);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void removed()
    {
        super.removed();
        OrderStatusPayload.clientReceiver = ignored -> {};
    }

    private record OrderView(UUID id, String target, long requested, int next, int total,
                             boolean blockingMode, String status, String message, long revision,
                             List<StepView> steps)
    {
        boolean active() { return status.equals("QUEUED") || status.equals("RUNNING") || status.equals("PAUSED"); }
    }

    private record StepView(IStackKey<?> key, long completed, long required, String fallback) {}
}
