package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner;
import com.amicbeam.beyondcraftlines.common.network.CancelOrderPayload;
import com.amicbeam.beyondcraftlines.common.network.OrderStatusPayload;
import com.amicbeam.beyondcraftlines.common.network.NetworkAmountPayload;
import com.amicbeam.beyondcraftlines.common.network.PlanPreviewPayload;
import com.amicbeam.beyondcraftlines.common.network.PlanProposalUploadPayload;
import com.amicbeam.beyondcraftlines.common.network.PlanningSnapshotPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestNetworkAmountPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestOrderStatusPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestPlanningSnapshotPayload;
import com.amicbeam.beyondcraftlines.common.network.SubmitOrderPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
    private Button orderButton;
    private boolean blockingMode;
    private ResourceLocation networkAmountTarget;
    private long networkAmount = -1;
    private double treeOffsetX = 18;
    private double treeOffsetY = 14;
    private double treeZoom = 1.0;
    private GraphNode treeRoot;
    private List<GraphNode> treeNodes = List.of();
    private GraphNode highlightedCanonical;
    private int canonicalHighlightTicks;
    private final Map<ResourceLocation, ResourceLocation> recipeOverrides = new LinkedHashMap<>();
    private final Map<IngredientSlotKey, ResourceLocation> ingredientOverrides = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> automaticRecipes = new LinkedHashMap<>();
    private final Map<IngredientSlotKey, ResourceLocation> automaticIngredients = new LinkedHashMap<>();
    private long previewNonce;
    private int previewDelay;
    private boolean previewDirty;
    private String previewError = "";
    private int previewNextPage;
    private int snapshotNextPage;
    private final Map<ResourceLocation, Long> planningStock = new LinkedHashMap<>();
    private ClientRecipePlanner.Catalog planningCatalog;
    private long proposalStockRevision;
    private long proposalRecipeEpoch;
    private boolean proposalReady;
    private boolean planningSnapshotValid;
    private long planningSnapshotCapturedAt;
    private long planningSnapshotRevision;
    private long planningRecipeEpoch;
    private int planningMaxDepth;
    private int planningMaxNodes;

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
        amount.setResponder(ignored -> markPreviewDirty());
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
        orderButton = addRenderableWidget(Button.builder(Component.translatable("gui.beyond_craftlines.order"), ignored -> submit())
                .bounds(rightX + actionWidth + 4, topPos + 128,
                        rightWidth - actionWidth - 4, 20).build());
        orderButton.active = false;
        updateBlockingButton();
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.beyond_craftlines.cancel"), ignored -> cancelLatest())
                .bounds(rightX, topPos + imageHeight - 30, rightWidth, 20).build());
        cancelButton.setTooltip(Tooltip.create(Component.translatable("tooltip.beyond_craftlines.cancel")));

        OrderStatusPayload.clientReceiver = this::receiveOrders;
        NetworkAmountPayload.clientReceiver = this::receiveNetworkAmount;
        PlanPreviewPayload.clientReceiver = this::receivePlanPreview;
        PlanningSnapshotPayload.clientReceiver = this::receivePlanningSnapshot;
        planningCatalog = ClientRecipePlanner.capture(minecraft.level, menu.recipes());
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
        if (canonicalHighlightTicks > 0) canonicalHighlightTicks--;
        if (++statusTicks >= 40)
        {
            statusTicks = 0;
            PacketDistributor.sendToServer(new RequestOrderStatusPayload());
            requestNetworkAmount();
        }
        if (previewDirty && ++previewDelay >= 5) requestPlanPreview();
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
            recipeOverrides.clear();
            ingredientOverrides.clear();
            automaticRecipes.clear();
            automaticIngredients.clear();
            ResourceLocation output = outputId(selected);
            if (menu.recipesForOutput(output).size() > 1) recipeOverrides.put(output, selected.id());
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
        if (selected == null || minecraft.level == null || !proposalReady) return;
        ItemStack result = selected.value().getResultItem(minecraft.level.registryAccess());
        PacketDistributor.sendToServer(new SubmitOrderPayload(
                BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), amountValue(), blockingMode,
                previewNonce, proposalStockRevision, proposalRecipeEpoch));
        markPreviewDirty();
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
                if (!ViewportCulling.intersects(treeLeft(), treeTop(), treeRight(), treeBottom(),
                        Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1)) continue;
                int mid = (x1 + x2) / 2;
                line(graphics, x1, y1, mid, y1, BD_BLUE);
                line(graphics, mid, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, BD_BLUE);
                line(graphics, mid, y2, x2, y2, BD_CYAN);
            }
        }
        GraphNode hovered = null;
        for (GraphNode node : nodes)
        {
            int x = nodeX(node);
            int y = nodeY(node);
            if (!ViewportCulling.intersects(treeLeft(), treeTop(), treeRight(), treeBottom(),
                    x - 1, y - 1, x + 29, y + 29)) continue;
            renderNode(graphics, node, mouseX, mouseY);
            if (mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28) hovered = node;
        }
        graphics.disableScissor();

        if (hovered != null) graphics.renderTooltip(font, hovered.stack, mouseX, mouseY);

        String zoom = Math.round(treeZoom * 100) + "%";
        graphics.drawString(font, zoom, treeRight() - font.width(zoom) - 5, treeBottom() - 12, 0x8296A8, false);
        Component resolutionHelp = Component.literal(font.plainSubstrByWidth(
                Component.translatable("gui.beyond_craftlines.tree_resolution_help").getString(),
                Math.max(0, treeRight() - treeLeft() - font.width(zoom) - 16)));
        graphics.drawString(font, resolutionHelp,
                treeLeft() + 5, treeBottom() - 12, 0x8296A8, false);
        if (!previewError.isBlank())
            graphics.drawString(font, font.plainSubstrByWidth(previewError, treeRight() - treeLeft() - 10),
                    treeLeft() + 5, treeBottom() - 24, 0xFFFF6677, true);
    }

    private void rebuildTree()
    { rebuildTree(true); }

    private void rebuildTree(boolean requestPreview)
    {
        if (selected == null || minecraft.level == null)
        {
            treeRoot = null;
            treeNodes = List.of();
            return;
        }
        RecipeHolder<?> rootRecipe = selectedRecipe(outputId(selected), selected);
        LinkedHashMap<ResourceLocation, GraphNode> canonicalNodes = new LinkedHashMap<>();
        treeRoot = buildTree(rootRecipe.value().getResultItem(minecraft.level.registryAccess()).copy(), rootRecipe,
                null, -1, 0, canonicalNodes, new HashSet<>());
        layout(treeRoot, new int[]{0});
        List<GraphNode> nodes = new ArrayList<>();
        flatten(treeRoot, nodes);
        treeNodes = List.copyOf(nodes);
        highlightedCanonical = null;
        canonicalHighlightTicks = 0;
        if (requestPreview) markPreviewDirty();
    }

    private int nodeX(GraphNode node) { return treeLeft() + (int) treeOffsetX + (int) (node.depth * 78 * treeZoom); }
    private int nodeY(GraphNode node) { return treeTop() + (int) treeOffsetY + (int) (node.row * 46 * treeZoom); }

    private void renderNode(GuiGraphics graphics, GraphNode node, int mouseX, int mouseY)
    {
        int x = nodeX(node);
        int y = nodeY(node);
        boolean hover = mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28;
        int edge = node.cyclicReference ? 0xFFB23A48
                : node.referenceTarget != null ? BD_VIOLET
                : node.depth == 0 ? BD_ORANGE : node.children.isEmpty() ? BD_VIOLET : BD_BLUE;
        if (node == highlightedCanonical && canonicalHighlightTicks > 0) edge = BD_ORANGE;
        graphics.fill(x - 1, y - 1, x + 29, y + 29, PANEL_SHADOW);
        graphics.fill(x, y, x + 28, y + 28, hover ? 0xFF293C50 : 0xFF142131);
        graphics.fill(x, y, x + 28, y + 1, edge);
        graphics.fill(x, y + 27, x + 28, y + 28, edge);
        graphics.fill(x, y, x + 1, y + 28, edge);
        graphics.fill(x + 27, y, x + 28, y + 28, edge);
        graphics.renderItem(node.stack, x + 6, y + 6);
        graphics.renderItemDecorations(font, node.stack, x + 6, y + 6);
        if (node.referenceTarget != null)
            graphics.drawString(font, node.cyclicReference ? "!" : ">", x + 20, y + 2,
                    node.cyclicReference ? 0xFFFF6677 : 0xFFFFFFFF, true);
        if (recipeOverrides.containsKey(node.itemId)) graphics.fill(x + 2, y + 2, x + 6, y + 6, BD_ORANGE);
        if (node.parentRecipe != null && ingredientOverrides.containsKey(
                new IngredientSlotKey(node.parentRecipe, node.parentSlot)))
            graphics.fill(x + 2, y + 22, x + 6, y + 26, BD_VIOLET);
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color)
    {
        graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private GraphNode buildTree(ItemStack stack, RecipeHolder<?> recipe, ResourceLocation parentRecipe,
                                int parentSlot, int depth,
                                Map<ResourceLocation, GraphNode> canonicalNodes,
                                Set<ResourceLocation> expanding)
    {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        GraphNode canonical = canonicalNodes.get(itemId);
        if (canonical != null)
            return GraphNode.reference(stack, itemId, recipe, parentRecipe, parentSlot, depth,
                    canonical, expanding.contains(itemId));

        GraphNode node = new GraphNode(stack, itemId, recipe, parentRecipe, parentSlot, depth);
        canonicalNodes.put(itemId, node);
        if (recipe == null) return node;
        expanding.add(itemId);
        int slot = 0;
        for (var ingredient : recipe.value().getIngredients())
        {
            int currentSlot = slot++;
            if (ingredient.isEmpty() || ingredient.getItems().length == 0) continue;
            ItemStack input = selectedIngredient(recipe.id(), currentSlot, ingredient.getItems()).copy();
            ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
            RecipeHolder<?> childRecipe = selectedRecipe(inputId, menu.recipeForOutput(inputId));
            node.children.add(buildTree(input, childRecipe, recipe.id(), currentSlot,
                    depth + 1, canonicalNodes, expanding));
        }
        expanding.remove(itemId);
        return node;
    }

    private RecipeHolder<?> selectedRecipe(ResourceLocation output, RecipeHolder<?> fallback)
    {
        List<RecipeHolder<?>> candidates = menu.recipesForOutput(output);
        if (candidates.isEmpty()) return fallback;
        ResourceLocation selectedId = recipeOverrides.get(output);
        if (selectedId == null) selectedId = automaticRecipes.get(output);
        if (selectedId != null)
            for (RecipeHolder<?> candidate : candidates) if (candidate.id().equals(selectedId)) return candidate;
        return fallback != null && candidates.stream().anyMatch(candidate -> candidate.id().equals(fallback.id()))
                ? fallback : candidates.getFirst();
    }

    private ItemStack selectedIngredient(ResourceLocation recipe, int slot, ItemStack[] candidates)
    {
        ResourceLocation selectedId = ingredientOverrides.get(new IngredientSlotKey(recipe, slot));
        if (selectedId == null) selectedId = automaticIngredients.get(new IngredientSlotKey(recipe, slot));
        if (selectedId != null)
            for (ItemStack candidate : candidates)
                if (BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selectedId)) return candidate;
        return candidates[0];
    }

    private ResourceLocation outputId(RecipeHolder<?> holder)
    { return BuiltInRegistries.ITEM.getKey(holder.value().getResultItem(minecraft.level.registryAccess()).getItem()); }

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
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && overTree(mouseX, mouseY))
        {
            GraphNode node = nodeAt(mouseX, mouseY);
            if (node != null && node.referenceTarget != null)
            {
                focusCanonical(node.referenceTarget);
                return true;
            }
        }
        if (button == 1 && overTree(mouseX, mouseY))
        {
            GraphNode node = nodeAt(mouseX, mouseY);
            if (node != null)
            {
                if (Screen.hasControlDown()) clearResolution(node, Screen.hasShiftDown());
                else if (Screen.hasShiftDown()) cycleIngredient(node);
                else cycleRecipe(node);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void focusCanonical(GraphNode target)
    {
        treeOffsetX = (treeRight() - treeLeft()) / 2.0 - target.depth * 78 * treeZoom - 14;
        treeOffsetY = (treeBottom() - treeTop()) / 2.0 - target.row * 46 * treeZoom - 14;
        highlightedCanonical = target;
        canonicalHighlightTicks = 40;
    }

    private GraphNode nodeAt(double mouseX, double mouseY)
    {
        for (GraphNode node : treeNodes)
        {
            int x = nodeX(node);
            int y = nodeY(node);
            if (mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28) return node;
        }
        return null;
    }

    private void cycleRecipe(GraphNode node)
    {
        if (node.referenceTarget != null) node = node.referenceTarget;
        List<RecipeHolder<?>> candidates = menu.recipesForOutput(node.itemId);
        if (candidates.size() < 2) return;
        ResourceLocation current = node.recipe == null ? null : node.recipe.id();
        int index = 0;
        for (int i = 0; i < candidates.size(); i++)
            if (candidates.get(i).id().equals(current)) { index = i; break; }
        recipeOverrides.put(node.itemId, candidates.get((index + 1) % candidates.size()).id());
        rebuildTree();
    }

    private void cycleIngredient(GraphNode node)
    {
        if (node.parentRecipe == null || node.parentSlot < 0) return;
        RecipeHolder<?> parent = menu.recipes().stream()
                .filter(holder -> holder.id().equals(node.parentRecipe)).findFirst().orElse(null);
        if (parent == null || node.parentSlot >= parent.value().getIngredients().size()) return;
        ItemStack[] candidates = parent.value().getIngredients().get(node.parentSlot).getItems();
        if (candidates.length < 2) return;
        int index = 0;
        for (int i = 0; i < candidates.length; i++)
            if (BuiltInRegistries.ITEM.getKey(candidates[i].getItem()).equals(node.itemId)) { index = i; break; }
        ResourceLocation next = BuiltInRegistries.ITEM.getKey(candidates[(index + 1) % candidates.length].getItem());
        ingredientOverrides.put(new IngredientSlotKey(node.parentRecipe, node.parentSlot), next);
        rebuildTree();
    }

    private void clearResolution(GraphNode node, boolean ingredient)
    {
        if (ingredient && node.parentRecipe != null)
            ingredientOverrides.remove(new IngredientSlotKey(node.parentRecipe, node.parentSlot));
        else recipeOverrides.remove(node.itemId);
        rebuildTree();
    }

    private void markPreviewDirty()
    {
        previewDirty = true;
        previewDelay = 0;
        previewNonce++;
        proposalReady = false;
        if (orderButton != null) orderButton.active = false;
    }

    private void requestPlanPreview()
    {
        previewDirty = false;
        previewDelay = 0;
        if (selected == null || minecraft.level == null) return;
        ResourceLocation target = outputId(selected);
        long nonce = previewNonce;
        previewNextPage = 0;
        snapshotNextPage = 0;
        previewError = "";
        if (planningSnapshotValid && minecraft.level.getGameTime() - planningSnapshotCapturedAt <= 20)
        {
            startClientPlanning(nonce, target, amountValue(), planningSnapshotRevision, planningRecipeEpoch,
                    planningMaxDepth, planningMaxNodes, Map.copyOf(planningStock),
                    Map.copyOf(recipeOverrides), Map.copyOf(ingredientOverrides));
            return;
        }
        planningStock.clear();
        PacketDistributor.sendToServer(new RequestPlanningSnapshotPayload(nonce, target.toString()));
    }

    private void receivePlanningSnapshot(PlanningSnapshotPayload snapshot)
    {
        if (selected == null || snapshot.nonce() != previewNonce
                || !snapshot.itemId().equals(outputId(selected).toString())) return;
        var header = snapshot.header();
        if (!header.status().success())
        {
            planningStock.clear();
            planningSnapshotValid = false;
            snapshotNextPage = 0;
            previewError = header.status().error();
            return;
        }
        if (header.pageCount() < 1 || header.pageIndex() != snapshotNextPage
                || header.pageIndex() < 0 || header.pageIndex() >= header.pageCount())
        {
            planningStock.clear();
            planningSnapshotValid = false;
            snapshotNextPage = 0;
            previewError = "invalid planning snapshot page sequence";
            return;
        }
        if (header.pageIndex() == 0) planningStock.clear();
        for (PlanningSnapshotPayload.Entry entry : snapshot.entries())
        {
            ResourceLocation item = ResourceLocation.tryParse(entry.item());
            if (item != null && entry.amount() > 0) planningStock.put(item, entry.amount());
        }
        snapshotNextPage++;
        if (snapshotNextPage < header.pageCount()) return;
        snapshotNextPage = 0;
        planningSnapshotValid = true;
        planningSnapshotCapturedAt = minecraft.level.getGameTime();
        planningSnapshotRevision = header.stockRevision();
        planningRecipeEpoch = header.recipeEpoch();
        planningMaxDepth = header.limits().maxDepth();
        planningMaxNodes = header.limits().maxNodes();
        startClientPlanning(snapshot.nonce(), outputId(selected), amountValue(), header.stockRevision(),
                header.recipeEpoch(), header.limits().maxDepth(), header.limits().maxNodes(),
                Map.copyOf(planningStock), Map.copyOf(recipeOverrides), Map.copyOf(ingredientOverrides));
    }

    private void startClientPlanning(long nonce, ResourceLocation target, long count,
                                     long stockRevision, long recipeEpoch, int maxDepth, int maxNodes,
                                     Map<ResourceLocation, Long> stock,
                                     Map<ResourceLocation, ResourceLocation> manualRecipes,
                                     Map<IngredientSlotKey, ResourceLocation> manualIngredients)
    {
        Map<ClientRecipePlanner.IngredientKey, ResourceLocation> ingredients = new LinkedHashMap<>();
        manualIngredients.forEach((key, value) -> ingredients.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        java.util.concurrent.CompletableFuture.supplyAsync(() -> ClientRecipePlanner.plan(planningCatalog,
                        stock, target, count, manualRecipes, ingredients, maxDepth, maxNodes))
                .whenComplete((proposal, failure) -> minecraft.execute(() -> {
                    if (nonce != previewNonce || selected == null || !target.equals(outputId(selected))) return;
                    if (failure != null)
                    {
                        previewError = failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage();
                        return;
                    }
                    if (!proposal.craftable())
                    {
                        previewError = "missing: " + proposal.missing();
                        return;
                    }
                    uploadProposal(nonce, target, count, stockRevision, recipeEpoch, proposal);
                }));
    }

    private void uploadProposal(long nonce, ResourceLocation target, long count, long stockRevision,
                                long recipeEpoch, ClientRecipePlanner.Proposal proposal)
    {
        List<SubmitOrderPayload.RecipeChoice> recipes = proposal.recipes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                .map(entry -> new SubmitOrderPayload.RecipeChoice(
                        entry.getKey().toString(), entry.getValue().toString())).toList();
        List<SubmitOrderPayload.IngredientChoice> ingredients = proposal.ingredients().entrySet().stream()
                .sorted(java.util.Comparator.comparing((Map.Entry<ClientRecipePlanner.IngredientKey, ResourceLocation> entry)
                                -> entry.getKey().recipe().toString()).thenComparingInt(entry -> entry.getKey().slot()))
                .map(entry -> new SubmitOrderPayload.IngredientChoice(entry.getKey().recipe().toString(),
                        entry.getKey().slot(), entry.getValue().toString())).toList();
        int pageCount = Math.max(1, Math.max((recipes.size() + 255) / 256, (ingredients.size() + 255) / 256));
        if (pageCount > 64)
        {
            previewError = "client proposal exceeds the upload limit";
            return;
        }
        proposalStockRevision = stockRevision;
        proposalRecipeEpoch = recipeEpoch;
        for (int page = 0; page < pageCount; page++)
        {
            int recipeFrom = Math.min(recipes.size(), page * 256);
            int ingredientFrom = Math.min(ingredients.size(), page * 256);
            PacketDistributor.sendToServer(new PlanProposalUploadPayload(nonce, target.toString(),
                    new PlanProposalUploadPayload.Header(count, stockRevision, recipeEpoch, page, pageCount),
                    recipes.subList(recipeFrom, Math.min(recipes.size(), recipeFrom + 256)),
                    ingredients.subList(ingredientFrom, Math.min(ingredients.size(), ingredientFrom + 256))));
        }
    }

    private void receivePlanPreview(PlanPreviewPayload preview)
    {
        if (selected == null || preview.nonce() != previewNonce || !preview.itemId().equals(outputId(selected).toString()))
            return;
        if (!preview.success())
        {
            proposalReady = false;
            if (orderButton != null) orderButton.active = false;
            automaticRecipes.clear();
            automaticIngredients.clear();
            previewNextPage = 0;
            previewError = preview.error();
            rebuildTree(false);
            if ("stale".equals(preview.failureKind()))
            {
                planningSnapshotValid = false;
                markPreviewDirty();
            }
            return;
        }
        if (preview.pageCount() < 1 || preview.pageIndex() < 0 || preview.pageIndex() >= preview.pageCount()
                || preview.pageIndex() != previewNextPage)
        {
            proposalReady = false;
            if (orderButton != null) orderButton.active = false;
            automaticRecipes.clear();
            automaticIngredients.clear();
            previewNextPage = 0;
            previewError = "invalid plan preview page sequence";
            rebuildTree(false);
            return;
        }
        if (preview.pageIndex() == 0)
        {
            automaticRecipes.clear();
            automaticIngredients.clear();
            previewError = "";
        }
        for (SubmitOrderPayload.RecipeChoice choice : preview.recipeChoices())
        {
            ResourceLocation output = ResourceLocation.tryParse(choice.output());
            ResourceLocation recipe = ResourceLocation.tryParse(choice.recipe());
            if (output != null && recipe != null) automaticRecipes.put(output, recipe);
        }
        for (SubmitOrderPayload.IngredientChoice choice : preview.ingredientChoices())
        {
            ResourceLocation recipe = ResourceLocation.tryParse(choice.recipe());
            ResourceLocation item = ResourceLocation.tryParse(choice.item());
            if (recipe != null && item != null && choice.slot() >= 0)
                automaticIngredients.put(new IngredientSlotKey(recipe, choice.slot()), item);
        }
        previewNextPage++;
        if (previewNextPage >= preview.pageCount())
        {
            previewNextPage = 0;
            proposalReady = true;
            if (orderButton != null) orderButton.active = true;
            rebuildTree(false);
        }
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
        PlanPreviewPayload.clientReceiver = ignored -> {};
        PlanningSnapshotPayload.clientReceiver = ignored -> {};
    }

    private static final class GraphNode
    {
        private final ItemStack stack;
        private final ResourceLocation itemId;
        private final RecipeHolder<?> recipe;
        private final ResourceLocation parentRecipe;
        private final int parentSlot;
        private final int depth;
        private final List<GraphNode> children = new ArrayList<>();
        private GraphNode referenceTarget;
        private boolean cyclicReference;
        private double row;

        private GraphNode(ItemStack stack, ResourceLocation itemId, RecipeHolder<?> recipe,
                          ResourceLocation parentRecipe, int parentSlot, int depth)
        {
            this.stack = stack;
            this.itemId = itemId;
            this.recipe = recipe;
            this.parentRecipe = parentRecipe;
            this.parentSlot = parentSlot;
            this.depth = depth;
        }

        private static GraphNode reference(ItemStack stack, ResourceLocation itemId, RecipeHolder<?> recipe,
                                           ResourceLocation parentRecipe, int parentSlot, int depth,
                                           GraphNode target, boolean cyclic)
        {
            GraphNode reference = new GraphNode(stack, itemId, recipe, parentRecipe, parentSlot, depth);
            reference.referenceTarget = target;
            reference.cyclicReference = cyclic;
            return reference;
        }
    }

    private record IngredientSlotKey(ResourceLocation recipe, int slot) {}

    private record OrderView(UUID id, String target, long requested, int next, int total,
                             boolean blockingMode, String status, String message)
    {
        boolean active() { return status.equals("QUEUED") || status.equals("RUNNING") || status.equals("PAUSED"); }
    }
}
