package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.network.CancelOrderPayload;
import com.amicbeam.beyondcraftlines.common.network.OrderStatusPayload;
import com.amicbeam.beyondcraftlines.common.network.NetworkAmountPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestNetworkAmountPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestOrderStatusPayload;
import com.amicbeam.beyondcraftlines.common.network.SubmitOrderPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CraftlineOrderScreen extends AbstractContainerScreen<CraftlineOrderMenu>
{
    private static final int MAX_ROWS = 10;
    private static final int PANEL = 0xFFF0F0F0;
    private static final int PANEL_EDGE = 0xFF555B62;
    private static final int PANEL_SHADOW = 0xFF202A36;
    private static final int CANVAS = 0xF0101724;
    private static final int CANVAS_GRID = 0xFF1B2A3A;
    private static final int BD_BLUE = 0xFF009CEB;
    private static final int BD_CYAN = 0xFF5BC8FF;
    private static final int BD_VIOLET = 0xFF7560BA;
    private static final int BD_ORANGE = 0xFFE58F16;

    private EditBox search;
    private EditBox amount;
    private final List<Button> recipeButtons = new ArrayList<>();
    private List<RecipeHolder<?>> filtered = List.of();
    private RecipeHolder<?> selected;
    private int page;
    private int visibleRows;
    private int statusTicks;
    private List<OrderView> orders = List.of();
    private Button cancelButton;
    private Button previousButton;
    private Button nextButton;
    private Button blockingButton;
    private boolean blockingMode;
    private ResourceLocation networkAmountTarget;
    private long networkAmount = -1;
    private double treeOffsetX = 18;
    private double treeOffsetY = 14;
    private double treeZoom = 1.0;
    private GraphNode treeRoot;
    private List<GraphNode> treeNodes = List.of();

    public CraftlineOrderScreen(CraftlineOrderMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
    }

    @Override
    protected void init()
    {
        imageWidth = Math.min(620, Math.max(300, width - 16));
        imageHeight = Math.min(360, Math.max(236, height - 16));
        super.init();
        visibleRows = Math.max(4, Math.min(MAX_ROWS, (imageHeight - 92) / 22));

        search = new EditBox(font, leftPos + 10, topPos + 28, leftPanelWidth() - 20, 18,
                Component.translatable("gui.beyond_craftlines.search"));
        search.setHint(Component.translatable("gui.beyond_craftlines.search"));
        search.setResponder(value -> { page = 0; refresh(); });
        addRenderableWidget(search);

        for (int row = 0; row < MAX_ROWS; row++)
        {
            final int index = row;
            Button button = Button.builder(Component.empty(), ignored -> select(index))
                    .bounds(leftPos + 10, topPos + 50 + row * 22, leftPanelWidth() - 20, 20).build();
            recipeButtons.add(addRenderableWidget(button));
        }
        int pagerY = topPos + imageHeight - 26;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            if (page > 0) { page--; refresh(); }
        }).bounds(leftPos + 10, pagerY, 28, 18).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            if ((page + 1) * visibleRows < filtered.size()) { page++; refresh(); }
        }).bounds(leftPos + leftPanelWidth() - 38, pagerY, 28, 18).build());

        int rightX = rightPanelLeft() + 10;
        int rightWidth = rightPanelWidth() - 20;
        amount = new EditBox(font, rightX, topPos + 84, rightWidth - 48, 18,
                Component.translatable("gui.beyond_craftlines.amount"));
        amount.setValue("1");
        amount.setFilter(value -> value.matches("[0-9]{0,19}")
                && (value.isEmpty() || parsesPositiveLong(value)));
        addRenderableWidget(amount);
        addRenderableWidget(Button.builder(Component.literal("+1"), ignored -> adjustAmount(1))
                .bounds(rightX + rightWidth - 44, topPos + 84, 44, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+64"), ignored -> adjustAmount(64))
                .bounds(rightX, topPos + 106, (rightWidth - 4) / 2, 18).build());
        addRenderableWidget(Button.builder(Component.literal("÷2"), ignored -> halveAmount())
                .bounds(rightX + (rightWidth + 4) / 2, topPos + 106, (rightWidth - 4) / 2, 18).build());
        int actionWidth = (rightWidth - 4) / 2;
        blockingButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            blockingMode = !blockingMode;
            updateBlockingButton();
        }).bounds(rightX, topPos + 128, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.beyond_craftlines.order"), ignored -> submit())
                .bounds(rightX + actionWidth + 4, topPos + 128,
                        rightWidth - actionWidth - 4, 20).build());
        updateBlockingButton();
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.beyond_craftlines.cancel"), ignored -> cancelLatest())
                .bounds(rightX, topPos + imageHeight - 30, rightWidth, 20).build());

        OrderStatusPayload.clientReceiver = this::receiveOrders;
        NetworkAmountPayload.clientReceiver = this::receiveNetworkAmount;
        PacketDistributor.sendToServer(new RequestOrderStatusPayload());
        selectInitialTarget();
        refresh();
    }

    private int leftPanelWidth() { return imageWidth < 400 ? 92 : imageWidth < 520 ? 124 : 148; }
    private int rightPanelWidth() { return imageWidth < 400 ? 104 : imageWidth < 520 ? 126 : 154; }
    private int rightPanelLeft() { return leftPos + imageWidth - rightPanelWidth(); }
    private int treeLeft() { return leftPos + leftPanelWidth() + 6; }
    private int treeRight() { return rightPanelLeft() - 6; }
    private int treeTop() { return topPos + 28; }
    private int treeBottom() { return topPos + imageHeight - 10; }
    private boolean overTree(double x, double y) { return x >= treeLeft() && x < treeRight() && y >= treeTop() && y < treeBottom(); }

    @Override protected void containerTick()
    {
        super.containerTick();
        if (++statusTicks >= 40)
        {
            statusTicks = 0;
            PacketDistributor.sendToServer(new RequestOrderStatusPayload());
            requestNetworkAmount();
        }
    }

    private void receiveOrders(net.minecraft.nbt.CompoundTag root)
    {
        List<OrderView> next = new ArrayList<>();
        var list = root.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            var value = list.getCompound(i);
            next.add(new OrderView(value.getUUID("id"), value.getString("target"), value.getLong("requested"),
                    value.getInt("next"), value.getInt("total"), value.getBoolean("blocking_mode"),
                    value.getString("status"), value.getString("message")));
        }
        orders = List.copyOf(next);
        if (cancelButton != null) cancelButton.active = orders.stream().anyMatch(OrderView::active);
    }

    private void cancelLatest()
    {
        orders.stream().filter(OrderView::active).findFirst().ifPresent(order -> {
            PacketDistributor.sendToServer(new CancelOrderPayload(order.id()));
            PacketDistributor.sendToServer(new RequestOrderStatusPayload());
        });
    }

    private void refresh()
    {
        if (search == null || minecraft.level == null) return;
        String needle = search.getValue().toLowerCase(Locale.ROOT);
        filtered = menu.recipes().stream().filter(holder -> {
            ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
            return needle.isBlank() || id.toString().contains(needle)
                    || result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
        int pages = Math.max(1, (filtered.size() + visibleRows - 1) / visibleRows);
        page = Math.min(page, pages - 1);
        for (int row = 0; row < recipeButtons.size(); row++)
        {
            int index = page * visibleRows + row;
            Button button = recipeButtons.get(row);
            button.visible = row < visibleRows && index < filtered.size();
            if (button.visible)
            {
                ItemStack stack = filtered.get(index).value().getResultItem(minecraft.level.registryAccess());
                String name = font.plainSubstrByWidth(stack.getHoverName().getString(), leftPanelWidth() - 42);
                button.setMessage(Component.literal("   " + name + " ×" + stack.getCount()));
            }
        }
        previousButton.active = page > 0;
        nextButton.active = (page + 1) * visibleRows < filtered.size();
    }

    private void select(int row)
    {
        int index = page * visibleRows + row;
        if (index < filtered.size())
        {
            selected = filtered.get(index);
            treeOffsetX = 18;
            treeOffsetY = 14;
            treeZoom = 1.0;
            rebuildTree();
            requestNetworkAmount();
        }
    }

    private void selectInitialTarget()
    {
        if (minecraft.level == null) return;
        selected = menu.recipes().stream().filter(holder -> {
            ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
            return BuiltInRegistries.ITEM.getKey(result.getItem()).equals(menu.initialTarget());
        }).findFirst().orElse(null);
        rebuildTree();
        requestNetworkAmount();
    }

    private void requestNetworkAmount()
    {
        if (selected == null || minecraft.level == null) return;
        ItemStack result = selected.value().getResultItem(minecraft.level.registryAccess());
        ResourceLocation target = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (!target.equals(networkAmountTarget)) networkAmount = -1;
        networkAmountTarget = target;
        PacketDistributor.sendToServer(new RequestNetworkAmountPayload(networkAmountTarget.toString()));
    }

    private void receiveNetworkAmount(String itemId, Long value)
    {
        ResourceLocation received = ResourceLocation.tryParse(itemId);
        if (received != null && received.equals(networkAmountTarget)) networkAmount = Math.max(0, value);
    }

    private long amountValue()
    {
        try { return Math.max(1, Long.parseLong(amount.getValue())); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static boolean parsesPositiveLong(String value)
    {
        try { return Long.parseLong(value) >= 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    private void adjustAmount(long delta)
    {
        long current = amountValue();
        amount.setValue(Long.toString(Long.MAX_VALUE - current < delta ? Long.MAX_VALUE : current + delta));
    }
    private void halveAmount() { amount.setValue(Long.toString(Math.max(1, amountValue() / 2))); }

    private void submit()
    {
        if (selected == null || minecraft.level == null) return;
        ItemStack result = selected.value().getResultItem(minecraft.level.registryAccess());
        PacketDistributor.sendToServer(new SubmitOrderPayload(
                BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), amountValue(), blockingMode));
    }

    private void updateBlockingButton()
    {
        if (blockingButton == null) return;
        blockingButton.setMessage(Component.translatable(blockingMode
                ? "gui.beyond_craftlines.blocking_mode_on"
                : "gui.beyond_craftlines.blocking_mode_off"));
        blockingButton.setTooltip(Tooltip.create(Component.translatable(blockingMode
                ? "tooltip.beyond_craftlines.blocking_mode_on"
                : "tooltip.beyond_craftlines.blocking_mode_off")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos + 10, topPos + 9, 0x253545, false);
        graphics.drawString(font, Component.translatable("gui.beyond_craftlines.recipe_tree"),
                treeLeft() + 5, topPos + 10, 0x00609D, false);
        graphics.drawString(font, Component.translatable("gui.beyond_craftlines.orders"),
                rightPanelLeft() + 10, topPos + 156, 0x253545, false);

        renderProducts(graphics);
        renderTarget(graphics);
        renderOrders(graphics);
        renderTree(graphics, mouseX, mouseY);

        int pages = Math.max(1, (filtered.size() + visibleRows - 1) / visibleRows);
        graphics.drawCenteredString(font, (page + 1) + "/" + pages,
                leftPos + leftPanelWidth() / 2, topPos + imageHeight - 21, 0x465564);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderProducts(GuiGraphics graphics)
    {
        for (int row = 0; row < visibleRows; row++)
        {
            int index = page * visibleRows + row;
            if (index >= filtered.size()) break;
            int x = leftPos + 12;
            int y = topPos + 52 + row * 22;
            RecipeHolder<?> holder = filtered.get(index);
            if (selected != null && holder.id().equals(selected.id()))
            {
                graphics.fill(leftPos + 9, topPos + 49 + row * 22, leftPos + leftPanelWidth() - 9, topPos + 50 + row * 22, BD_BLUE);
                graphics.fill(leftPos + 9, topPos + 69 + row * 22, leftPos + leftPanelWidth() - 9, topPos + 70 + row * 22, BD_CYAN);
            }
            ItemStack stack = holder.value().getResultItem(minecraft.level.registryAccess());
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        }
    }

    private void renderTarget(GuiGraphics graphics)
    {
        int x = rightPanelLeft() + 10;
        int width = rightPanelWidth() - 20;
        if (selected == null)
        {
            graphics.drawCenteredString(font, Component.translatable("gui.beyond_craftlines.no_selection"),
                    x + width / 2, topPos + 48, 0x777777);
            return;
        }
        ItemStack result = selected.value().getResultItem(minecraft.level.registryAccess());
        graphics.renderItem(result, x, topPos + 36);
        graphics.renderItemDecorations(font, result, x, topPos + 36);
        String name = font.plainSubstrByWidth(result.getHoverName().getString(), width - 22);
        graphics.drawString(font, name, x + 22, topPos + 39, 0x253545, false);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
        graphics.drawString(font, font.plainSubstrByWidth(id.toString(), width), x, topPos + 56, 0x687784, false);
        Component owned = networkAmount < 0
                ? Component.translatable("gui.beyond_craftlines.network_owned_loading")
                : Component.translatable("gui.beyond_craftlines.network_owned", networkAmount);
        graphics.drawString(font, font.plainSubstrByWidth(owned.getString(), width), x, topPos + 66, 0x00609D, false);
        graphics.drawString(font, Component.translatable("gui.beyond_craftlines.amount"), x, topPos + 75, 0x465564, false);
    }

    private void renderOrders(GuiGraphics graphics)
    {
        int x = rightPanelLeft() + 10;
        int width = rightPanelWidth() - 20;
        int y = topPos + 170;
        int max = Math.max(1, (imageHeight - 212) / 28);
        int line = 0;
        for (OrderView order : orders)
        {
            if (line++ >= max) break;
            int color = order.status().equals("ERROR") ? 0xB23A48 : order.status().equals("COMPLETE") ? 0x257A55 : 0x34495E;
            String target = font.plainSubstrByWidth(shortTarget(order.target()) + " ×" + order.requested(), width);
            graphics.drawString(font, target, x, y, color, false);
            String mode = order.blockingMode() ? "[B] " : "";
            String progress = mode + (order.total() == 0 ? order.status()
                    : order.status() + " " + order.next() + "/" + order.total());
            String detail = order.message().isBlank() ? progress : progress + " · " + order.message();
            graphics.drawString(font, font.plainSubstrByWidth(detail, width), x + 5, y + 11, 0x6B7580, false);
            y += 28;
        }
    }

    private static String shortTarget(String value)
    {
        int split = value.indexOf(':');
        return split >= 0 ? value.substring(split + 1) : value;
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY)
    {
        if (treeRoot == null || minecraft.level == null) return;
        List<GraphNode> nodes = treeNodes;

        graphics.enableScissor(treeLeft() + 1, treeTop() + 1, treeRight() - 1, treeBottom() - 1);
        for (GraphNode node : nodes)
        {
            for (GraphNode child : node.children)
            {
                int x1 = nodeX(node) + 28;
                int y1 = nodeY(node) + 14;
                int x2 = nodeX(child);
                int y2 = nodeY(child) + 14;
                int mid = (x1 + x2) / 2;
                line(graphics, x1, y1, mid, y1, BD_BLUE);
                line(graphics, mid, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, BD_BLUE);
                line(graphics, mid, y2, x2, y2, BD_CYAN);
            }
        }
        GraphNode hovered = null;
        for (GraphNode node : nodes)
        {
            renderNode(graphics, node, mouseX, mouseY);
            int x = nodeX(node);
            int y = nodeY(node);
            if (mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28) hovered = node;
        }
        graphics.disableScissor();

        if (hovered != null) graphics.renderTooltip(font, hovered.stack, mouseX, mouseY);

        String zoom = Math.round(treeZoom * 100) + "%";
        graphics.drawString(font, zoom, treeRight() - font.width(zoom) - 5, treeBottom() - 12, 0x8296A8, false);
    }

    private void rebuildTree()
    {
        if (selected == null || minecraft.level == null)
        {
            treeRoot = null;
            treeNodes = List.of();
            return;
        }
        treeRoot = buildTree(selected, 0, new HashSet<>(), new int[]{0});
        layout(treeRoot, new int[]{0});
        List<GraphNode> nodes = new ArrayList<>();
        flatten(treeRoot, nodes);
        treeNodes = List.copyOf(nodes);
    }

    private int nodeX(GraphNode node) { return treeLeft() + (int) treeOffsetX + (int) (node.depth * 78 * treeZoom); }
    private int nodeY(GraphNode node) { return treeTop() + (int) treeOffsetY + (int) (node.row * 46 * treeZoom); }

    private void renderNode(GuiGraphics graphics, GraphNode node, int mouseX, int mouseY)
    {
        int x = nodeX(node);
        int y = nodeY(node);
        boolean hover = mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28;
        int edge = node.depth == 0 ? BD_ORANGE : node.children.isEmpty() ? BD_VIOLET : BD_BLUE;
        graphics.fill(x - 1, y - 1, x + 29, y + 29, PANEL_SHADOW);
        graphics.fill(x, y, x + 28, y + 28, hover ? 0xFF293C50 : 0xFF142131);
        graphics.fill(x, y, x + 28, y + 1, edge);
        graphics.fill(x, y + 27, x + 28, y + 28, edge);
        graphics.fill(x, y, x + 1, y + 28, edge);
        graphics.fill(x + 27, y, x + 28, y + 28, edge);
        graphics.renderItem(node.stack, x + 6, y + 6);
        graphics.renderItemDecorations(font, node.stack, x + 6, y + 6);
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color)
    {
        graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private GraphNode buildTree(RecipeHolder<?> recipe, int depth, Set<ResourceLocation> visiting, int[] count)
    {
        ItemStack output = recipe.value().getResultItem(minecraft.level.registryAccess()).copy();
        GraphNode node = new GraphNode(output, depth);
        if (depth >= 5 || count[0]++ >= 47) return node;
        for (var ingredient : recipe.value().getIngredients())
        {
            if (ingredient.isEmpty() || ingredient.getItems().length == 0 || count[0] >= 48) continue;
            ItemStack input = ingredient.getItems()[0].copy();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(input.getItem());
            GraphNode child = null;
            if (visiting.add(itemId))
            {
                RecipeHolder<?> childRecipe = menu.recipeForOutput(itemId);
                if (childRecipe != null) child = buildTree(childRecipe, depth + 1, visiting, count);
                visiting.remove(itemId);
            }
            if (child == null) child = new GraphNode(input, depth + 1);
            node.children.add(child);
        }
        return node;
    }

    private static void layout(GraphNode node, int[] nextRow)
    {
        if (node.children.isEmpty()) node.row = nextRow[0]++;
        else
        {
            for (GraphNode child : node.children) layout(child, nextRow);
            node.row = (node.children.getFirst().row + node.children.getLast().row) / 2.0;
        }
    }

    private static void flatten(GraphNode node, List<GraphNode> nodes)
    {
        nodes.add(node);
        for (GraphNode child : node.children) flatten(child, nodes);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if (overTree(mouseX, mouseY) && (button == 0 || button == 2))
        {
            treeOffsetX += dragX;
            treeOffsetY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (overTree(mouseX, mouseY))
        {
            double oldZoom = treeZoom;
            treeZoom = Math.max(0.55, Math.min(1.75, treeZoom + scrollY * 0.1));
            double anchorX = mouseX - treeLeft() - treeOffsetX;
            double anchorY = mouseY - treeTop() - treeOffsetY;
            treeOffsetX -= anchorX * (treeZoom / oldZoom - 1.0);
            treeOffsetY -= anchorY * (treeZoom / oldZoom - 1.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, PANEL_SHADOW);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.fill(treeLeft(), treeTop(), treeRight(), treeBottom(), CANVAS);
        for (int x = treeLeft() + 8; x < treeRight(); x += 16)
            graphics.fill(x, treeTop() + 1, x + 1, treeBottom() - 1, CANVAS_GRID);
        for (int y = treeTop() + 8; y < treeBottom(); y += 16)
            graphics.fill(treeLeft() + 1, y, treeRight() - 1, y + 1, CANVAS_GRID);
        graphics.fill(treeLeft() - 1, treeTop() - 1, treeRight() + 1, treeTop(), PANEL_EDGE);
        graphics.fill(treeLeft() - 1, treeBottom(), treeRight() + 1, treeBottom() + 1, 0xFFFFFFFF);
        graphics.fill(rightPanelLeft(), topPos + 24, rightPanelLeft() + 1, topPos + imageHeight - 8, 0xFF9AA2A9);
    }

    @Override public void removed()
    {
        super.removed();
        OrderStatusPayload.clientReceiver = ignored -> {};
        NetworkAmountPayload.clientReceiver = (ignoredId, ignoredAmount) -> {};
    }

    private static final class GraphNode
    {
        private final ItemStack stack;
        private final int depth;
        private final List<GraphNode> children = new ArrayList<>();
        private double row;

        private GraphNode(ItemStack stack, int depth)
        {
            this.stack = stack;
            this.depth = depth;
        }
    }

    private record OrderView(UUID id, String target, long requested, int next, int total,
                             boolean blockingMode, String status, String message)
    {
        boolean active() { return status.equals("QUEUED") || status.equals("RUNNING") || status.equals("PAUSED"); }
    }
}
