package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.CraftlinesConfig;
import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.crafting.ClientRecipePlanner;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService;
import com.amicbeam.beyondcraftlines.client.tooltip.RecipePreviewTooltip;
import com.amicbeam.beyondcraftlines.common.network.NetworkAmountPayload;
import com.amicbeam.beyondcraftlines.common.network.PlanPreviewPayload;
import com.amicbeam.beyondcraftlines.common.network.PlanProposalUploadPayload;
import com.amicbeam.beyondcraftlines.common.network.PlanningSnapshotPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestNetworkAmountPayload;
import com.amicbeam.beyondcraftlines.common.network.RequestPlanningSnapshotPayload;
import com.amicbeam.beyondcraftlines.common.network.SubmitOrderPayload;
import com.amicbeam.beyondcraftlines.common.network.SaveDashboardRecipePayload;
import com.amicbeam.beyondcraftlines.common.runtime.OrderOutputDestination;
import com.amicbeam.beyondcraftlines.common.dashboard.DashboardStockMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public final class CraftlineOrderScreen extends AbstractContainerScreen<CraftlineOrderMenu>
{
    private static final AtomicInteger PLANNER_THREAD_ID = new AtomicInteger();
    private static final ScheduledThreadPoolExecutor PLANNING_EXECUTOR = planningExecutor();
    private static final ScheduledThreadPoolExecutor OPTIMIZATION_EXECUTOR=planningExecutor();
    private static final int PANEL = 0xFFF0F0F0;
    private static final int PANEL_EDGE = 0xFF555B62;
    private static final int PANEL_SHADOW = 0xFF202A36;
    private static final int CANVAS = 0xF0101724;
    private static final int CANVAS_GRID = 0xFF1B2A3A;
    private static final int BD_BLUE = 0xFF009CEB;
    private static final int BD_CYAN = 0xFF5BC8FF;
    private static final int BD_VIOLET = 0xFF7560BA;
    private static final int BD_ORANGE = 0xFFE58F16;
    private static final int PICKER_COLUMNS = 8;
    private static final int PICKER_ROWS = 5;
    private static final int PICKER_PAGE_SIZE = PICKER_COLUMNS * PICKER_ROWS;
    private static final int PICKER_WIDTH = PICKER_COLUMNS * 20 + 8;
    private static final int PICKER_HEIGHT = PICKER_ROWS * 20 + 34;
    private static final int PICKER_HEADER_HEIGHT = 18;
    private static final int PICKER_Z = 300;
    private static final long[] AMOUNT_STEPS = {1, 10, 100, 1_000};
    private static final double DEFAULT_TREE_ZOOM = 0.65;
    private static final double MIN_TREE_ZOOM = 0.45;
    private static final double MAX_TREE_ZOOM = 1.75;

    private EditBox amount;
    private RecipeHolder<?> selected;
    private int refreshTicks;
    private Button blockingButton;
    private Button outputDestinationButton;
    private Button orderButton;
    private boolean blockingMode;
    private OrderOutputDestination outputDestination = OrderOutputDestination.NETWORK;
    private DashboardStockMode dashboardStockMode = DashboardStockMode.NETWORK;
    private String networkAmountTarget;
    private long networkAmount = -1;
    private double treeOffsetX = 18;
    private double treeOffsetY = 14;
    private double treeZoom = DEFAULT_TREE_ZOOM;
    private GraphNode treeRoot;
    private List<GraphNode> treeNodes = List.of();
    private boolean treeViewAdjusted;
    private GraphNode ingredientPickerNode;
    private List<ItemStack> ingredientPickerItems = List.of();
    private GraphNode recipePickerNode;
    private List<RecipeHolder<?>> recipePickerRecipes = List.of();
    private int ingredientPickerPage;
    private int ingredientPickerX;
    private int ingredientPickerY;
    private final Map<Identifier, Identifier> recipeOverrides = new LinkedHashMap<>();
    private final Map<String, Identifier> resourceRecipeOverrides = new LinkedHashMap<>();
    private final Map<IngredientSlotKey, String> ingredientOverrides = new LinkedHashMap<>();
    private final Map<Identifier, Identifier> automaticRecipes = new LinkedHashMap<>();
    private final Map<String, Identifier> automaticResourceRecipes = new LinkedHashMap<>();
    private final Map<IngredientSlotKey, String> automaticIngredients = new LinkedHashMap<>();
    private final Map<Identifier, Identifier> defaultRecipes = new LinkedHashMap<>();
    private final Map<String, Identifier> defaultResourceRecipes = new LinkedHashMap<>();
    private final Map<IngredientSlotKey, String> defaultIngredients = new LinkedHashMap<>();
    private long previewNonce;
    private int previewDelay;
    private int previewDelayTicks = 1;
    private boolean previewDirty;
    private boolean amountOnlyPreviewChange;
    private boolean requestedSnapshotPrefersAutomaticChoices;
    private String previewError = "";
    private int previewNextPage;
    private int materialScroll;
    private boolean materialSummaryReady;
    private boolean materialSummaryMissing;
    private boolean materialSummaryTheoretical;
    private int snapshotNextPage;
    private final Map<IStackKey<?>, Long> planningResources = new LinkedHashMap<>();
    private final Map<IStackKey<?>, Long> missingMaterials = new LinkedHashMap<>();
    private final Map<IStackKey<?>, Long> extractionMaterials = new LinkedHashMap<>();
    private final Map<IStackKey<?>, NodeMetric> nodeMetrics = new LinkedHashMap<>();
    private final Map<IStackKey<?>, Long> pendingMissingMaterials = new LinkedHashMap<>();
    private final Map<IStackKey<?>, Long> pendingExtractionMaterials = new LinkedHashMap<>();
    private final Map<IStackKey<?>, NodeMetric> pendingNodeMetrics = new LinkedHashMap<>();
    private final Set<Identifier> collapsedNodes = new HashSet<>();
    private ClientRecipePlanner.Catalog planningCatalog;
    private ClientPlanningCatalogWarmup.Handle planningCatalogBuilder;
    private long planningCatalogRevision = -1;
    private long planningCatalogBuildRevision = -1;
    private long proposalStockRevision;
    private long proposalRecipeEpoch;
    private boolean proposalReady;
    private com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome planningOutcome=com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.SEARCHING;
    private boolean submitWhenReady;
    private boolean planningSnapshotValid;
    private long planningSnapshotCapturedAt;
    private long planningSnapshotRevision;
    private long planningRecipeEpoch;
    private int planningMaxDepth;
    private int planningMaxNodes;
    private Future<?> planningTask;
    private Future<?> optimizationTask;
    private long planningGeneration;
    private boolean initialized;
    private boolean preferencesLoaded;
    private boolean terminalMenuError;
    private String loadingStatus = "";

    public CraftlineOrderScreen(CraftlineOrderMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, 620, 360);
        blockingMode = menu.initialBlockingMode();
        dashboardStockMode = DashboardStockMode.byId(menu.initialDashboardStockMode());
    }

    @Override
    protected void init()
    {
        String retainedAmount = amount == null ? Long.toString(menu.dashboardConfiguration()
                ? menu.initialDashboardDesired() : 1) : amount.getValue();
        super.init();
        terminalMenuError=!menu.initialError().isBlank();if(terminalMenuError)previewError=localizedPlanningError(menu.initialError());

        int rightX = rightPanelLeft() + 10;
        int rightWidth = rightPanelWidth() - 20;
        amount = new EditBox(font, rightX, topPos + 66, rightWidth, 18,
                Component.translatable("gui.beyond_craftlines.amount"));
        amount.setValue(retainedAmount);
        amount.setFilter(value -> value.matches("[0-9]{0,19}")
                && (value.isEmpty() || parsesPositiveLong(value)));
        amount.setResponder(ignored -> {
            markAmountPreviewDirty();
            rebuildTree(false);
        });
        addRenderableWidget(amount);
        addAmountButtons(rightX, rightWidth, topPos + 85, true);
        addAmountButtons(rightX, rightWidth, topPos + 102, false);
        outputDestinationButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            if (menu.dashboardConfiguration())
            {
                dashboardStockMode = dashboardStockMode.next();
                updateOutputDestinationButton();
                return;
            }
            outputDestination = outputDestination.next();
            updateOutputDestinationButton();
            if (!ClientPlannerPreferences.setOutputDestination(outputDestination)
                    && minecraft.player != null)
                minecraft.player.sendOverlayMessage(Component.translatable(
                        "error.beyond_craftlines.client_planner_preference_save_failed"));
        }).bounds(rightX, topPos + 120, rightWidth, 18).build());
        outputDestinationButton.active = menu.dashboardConfiguration()
                || menu.initialTarget() instanceof ItemStackKey;
        int actionWidth = (rightWidth - 4) / 2;
        blockingButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            blockingMode = !blockingMode;
            updateBlockingButton();
        }).bounds(rightX, topPos + 139, actionWidth, 18).build());
        orderButton = addRenderableWidget(Button.builder(Component.translatable(menu.dashboardConfiguration()
                        ? "gui.beyond_craftlines.save" : "gui.beyond_craftlines.order"),
                ignored -> { if (menu.dashboardConfiguration()) saveDashboardRecipe(); else submit(); })
                .bounds(rightX + actionWidth + 4, topPos + 139,
                        rightWidth - actionWidth - 4, 18).build());
        orderButton.active = canQueueOrder();
        updateBlockingButton();
        updateOutputDestinationButton();
        NetworkAmountPayload.clientReceiver = this::receiveNetworkAmount;
        PlanPreviewPayload.clientReceiver = this::receivePlanPreview;
        PlanningSnapshotPayload.clientReceiver = this::receivePlanningSnapshot;
        if(terminalMenuError){enterTerminalMenuError(menu.initialError());return;}
        JeiCatalystIndex.prewarmRecipeTypes(menu.availableFamilies());
        ClientPlanningCatalogWarmup.request(menu.availableFamilies());
        if (!initialized)
        {
            initialized = true;
            if (!JeiCatalystIndex.recipeTypesReady(menu.availableFamilies()))
                loadingStatus = jeiTypeIndexingText();
            else if (menu.recipeIndexComplete()) finishRecipeIndex();
            else loadingStatus = recipeLookupIndexingText();
        }
    }

    private int rightPanelWidth() { return imageWidth < 520 ? 126 : 154; }
    private boolean canQueueOrder()
    {
        return !terminalMenuError&&planningCatalog != null
                && (!menu.dashboardConfiguration() || proposalReady);
    }
    private int rightPanelLeft() { return leftPos + imageWidth - rightPanelWidth(); }
    private int treeLeft() { return leftPos + 10; }
    private int treeRight() { return rightPanelLeft() - 6; }
    private int treeTop() { return topPos + 28; }
    private int treeBottom() { return topPos + imageHeight - 10; }
    /** Keep graph quads out of the help/error footer; item rendering is buffered and must be clipped here. */
    private int treeContentBottom()
    {
        int lines = (previewError.isBlank() ? 0 : 1) + (loadingStatus.isBlank() ? 0 : 1);
        return treeBottom() - 18 - lines * 12;
    }
    private boolean overTree(double x, double y) { return x >= treeLeft() && x < treeRight() && y >= treeTop() && y < treeBottom(); }
    private boolean pickerOpen() { return ingredientPickerNode != null || recipePickerNode != null; }

    @Override protected void containerTick()
    {
        super.containerTick();
        if(terminalMenuError){loadingStatus="";if(orderButton!=null)orderButton.active=false;return;}
        boolean jeiTypesReady = JeiCatalystIndex.recipeTypesReady(menu.availableFamilies());
        if (!jeiTypesReady)
        {
            loadingStatus = jeiTypeIndexingText();
            return;
        }
        if (!preferencesLoaded && menu.recipeIndexComplete()) finishRecipeIndex();
        if (preferencesLoaded && planningCatalog == null && planningCatalogBuilder == null)
            beginPlanningCatalogCapture();
        long virtualRevision = com.amicbeam.beyondcraftlines.common.crafting
                .VirtualProvisionerRecipeRegistry.revision();
        if (planningCatalog != null && planningCatalogRevision != virtualRevision)
        {
            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                    "{} client planning catalog stale capturedRevision={} currentRevision={}",
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                    planningCatalogRevision, virtualRevision);
            ClientPlanningCatalogWarmup.invalidate();
            beginPlanningCatalogCapture();
        }
        if (planningCatalog == null && planningCatalogBuilder != null)
        {
            if (planningCatalogBuilder.complete())
            {
                planningCatalog=planningCatalogBuilder.catalog();
                planningCatalogRevision = planningCatalogBuildRevision;
                finishPlanningCatalogPreparation();
            }
            else loadingStatus=indexingRecipesText();
        }
        if (++refreshTicks >= 40)
        {
            refreshTicks = 0;
            requestNetworkAmount();
        }
        if (planningCatalog != null && previewDirty && ++previewDelay >= previewDelayTicks)
            requestPlanPreview();
    }

    private void finishRecipeIndex()
    {
        if (preferencesLoaded) return;
        preferencesLoaded = true;
        loadClientPreferences();
        selectInitialTarget();
        beginPlanningCatalogCapture();
    }

    private void beginPlanningCatalogCapture()
    {
        cancelPlanningTask();
        planningCatalog = null;
        planningCatalogBuildRevision = com.amicbeam.beyondcraftlines.common.crafting
                .VirtualProvisionerRecipeRegistry.revision();
        ClientPlanningCatalogWarmup.request(menu.availableFamilies());
        planningCatalogBuilder=ClientPlanningCatalogWarmup.handle();
        if (planningCatalogBuilder.complete())
        {
            planningCatalog=planningCatalogBuilder.catalog();
            planningCatalogRevision = planningCatalogBuildRevision;
            finishPlanningCatalogPreparation();
        }
        else loadingStatus = indexingRecipesText();
    }

    private void selectInitialTarget()
    {
        if (minecraft.level == null) return;
        selected = menu.initialRecipe() != null && menu.initialRecipeHolder() != null && menu.recipeProduces(
                        menu.initialRecipe(), menu.targetToken()) ? menu.initialRecipeHolder() : null;
        if (selected != null)
        {
            if (menu.initialRecipePinned() && menu.initialTarget() instanceof ItemStackKey itemKey)
                recipeOverrides.put(BuiltInRegistries.ITEM.getKey(itemKey.getSource()), menu.initialRecipe());
            else if (!menu.initialRecipePinned())
                selected = selectedResourceRecipe(menu.initialTarget(), selected);
        }
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} client screen initial target={} initialRecipe={} pinned={} selected={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(menu.initialTarget()),
                menu.initialRecipe(), menu.initialRecipePinned(),
                selected == null ? "null" : selected.id().identifier());
        rebuildTree();
        requestNetworkAmount();
    }

    private void enterTerminalMenuError(String error)
    {
        terminalMenuError=true;previewDirty=false;
        submitWhenReady=false;proposalReady=false;selected=null;cancelPlanningTask();loadingStatus="";
        previewError=localizedPlanningError(error);planningOutcome=com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.RUNTIME_UNAVAILABLE;
        clearDisplayMetrics();if(orderButton!=null)orderButton.active=false;rebuildTree(false);
    }

    private String indexingRecipesText()
    {
        int completed=planningCatalogBuilder==null?0:planningCatalogBuilder.completedRecipes();
        int total=planningCatalogBuilder==null?0:planningCatalogBuilder.totalRecipes();
        return Component.translatable("gui.beyond_craftlines.capturing_recipes",
                completed,total).getString();
    }

    private String planningText(){return Component.translatable(CraftlinesConfig.ENABLE_OPTIMAL_RECIPE_SEARCH.get()?"gui.beyond_craftlines.planning_tree":"gui.beyond_craftlines.validating_tree").getString();}

    private String recipeLookupIndexingText()
    {
        return Component.translatable("gui.beyond_craftlines.indexing_recipes",
                menu.indexedRecipeCandidates(), menu.totalRecipeCandidates()).getString();
    }

    private String jeiTypeIndexingText()
    { return Component.translatable("gui.beyond_craftlines.indexing_jei_types").getString(); }

    private void finishPlanningCatalogPreparation()
    {
        loadingStatus="";markPreviewDirty();
    }

    private void requestNetworkAmount()
    {
        if (minecraft.level == null) return;
        String target = menu.targetToken();
        if (!target.equals(networkAmountTarget)) networkAmount = -1;
        networkAmountTarget = target;
        ClientPacketDistributor.sendToServer(new RequestNetworkAmountPayload(networkAmountTarget));
    }

    private void receiveNetworkAmount(String itemId, Long value)
    {
        if (itemId.equals(networkAmountTarget)) networkAmount = Math.max(0, value);
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

    private void addAmountButtons(int left, int width, int y, boolean increase)
    {
        int gap = 2;
        int available = width - gap * (AMOUNT_STEPS.length - 1);
        int x = left;
        for (int index = 0; index < AMOUNT_STEPS.length; index++)
        {
            long step = AMOUNT_STEPS[index];
            long delta = increase ? step : -step;
            int buttonWidth = available / AMOUNT_STEPS.length
                    + (index < available % AMOUNT_STEPS.length ? 1 : 0);
            String label = (increase ? "+" : "-") + (step == 1_000 ? "1k" : step);
            addRenderableWidget(Button.builder(Component.literal(label), ignored -> adjustAmount(delta))
                    .bounds(x, y, buttonWidth, 16).build());
            x += buttonWidth + gap;
        }
    }

    private void adjustAmount(long delta)
    {
        long current = amountValue();
        long adjusted = delta > 0 && Long.MAX_VALUE - current < delta
                ? Long.MAX_VALUE : Math.max(1, current + delta);
        amount.setValue(Long.toString(adjusted));
    }

    private void submit()
    {
        if (minecraft.level == null) return;
        if (!proposalReady)
        {
            submitWhenReady = true;
            if (orderButton != null) orderButton.active = false;
            return;
        }
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} client submit nonce={} count={} stockRevision={} recipeEpoch={} target={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                previewNonce, amountValue(), proposalStockRevision, proposalRecipeEpoch,
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(menu.initialTarget()));
        ClientPacketDistributor.sendToServer(new SubmitOrderPayload(
                menu.targetToken(), amountValue(), blockingMode,
                outputDestination.id(), previewNonce, proposalStockRevision, proposalRecipeEpoch));
        submitWhenReady = false;
        markPreviewDirty();
    }

    private void saveDashboardRecipe()
    {
        if (minecraft.level == null || !proposalReady
                || menu.dashboardPosition() == null) return;
        ClientPacketDistributor.sendToServer(new SaveDashboardRecipePayload(menu.dashboardPosition(),
                amountValue(), dashboardStockMode.id(), previewNonce, proposalRecipeEpoch, blockingMode));
        onClose();
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

    private void updateOutputDestinationButton()
    {
        if (outputDestinationButton == null) return;
        if (menu.dashboardConfiguration())
        {
            outputDestinationButton.setMessage(Component.translatable(
                    "gui.beyond_craftlines.dashboard.stock_mode." + dashboardStockMode.id()));
            outputDestinationButton.setTooltip(Tooltip.create(Component.translatable(
                    "tooltip.beyond_craftlines.dashboard.stock_mode." + dashboardStockMode.id())));
            return;
        }
        outputDestinationButton.setMessage(Component.translatable(outputDestination
                == OrderOutputDestination.NETWORK
                ? "gui.beyond_craftlines.output_destination_network"
                : "gui.beyond_craftlines.output_destination_inventory"));
        outputDestinationButton.setTooltip(Tooltip.create(Component.translatable(outputDestination
                == OrderOutputDestination.NETWORK
                ? "tooltip.beyond_craftlines.output_destination_network"
                : "tooltip.beyond_craftlines.output_destination_inventory")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, leftPos + 10, topPos + 9, 0x253545, false);
        renderTarget(graphics);
        renderMaterials(graphics, mouseX, mouseY);
        renderTree(graphics, mouseX, mouseY);
        renderIngredientPicker(graphics, mouseX, mouseY);
    }

    private void renderTarget(GuiGraphicsExtractor graphics)
    {
        int x = rightPanelLeft() + 10;
        int width = rightPanelWidth() - 20;
        IStackKey<?> target = menu.initialTarget();
        target.getRender().render(graphics, target, x, topPos + 18);
        String name = font.plainSubstrByWidth(target.getRender().getDisplayName(target).getString(), width - 22);
        graphics.text(font, name, x + 22, topPos + 21, 0x253545, false);
        Component owned = networkAmount < 0
                ? Component.translatable("gui.beyond_craftlines.network_owned_loading")
                : Component.translatable("gui.beyond_craftlines.network_owned", networkAmount);
        graphics.text(font, font.plainSubstrByWidth(owned.getString(), width), x, topPos + 38, 0x00609D, false);
        graphics.text(font, Component.translatable("gui.beyond_craftlines.amount"), x, topPos + 57, 0x465564, false);
    }

    private int materialColumns() { return Math.max(1, (rightPanelWidth() - 20) / 22); }
    private int materialRows() { return Math.max(1, (imageHeight - 188) / 22); }
    private int materialPageSize() { return materialColumns() * materialRows(); }
    private int materialMaxScroll(int size)
    {
        int overflow = Math.max(0, size - materialPageSize());
        return (overflow + materialColumns() - 1) / materialColumns() * materialColumns();
    }

    private Map<IStackKey<?>, Long> visibleMaterials()
    {
        return materialSummaryMissing ? missingMaterials : extractionMaterials;
    }

    private boolean overMaterials(double mouseX, double mouseY)
    {
        return mouseX >= rightPanelLeft() + 8 && mouseX < leftPos + imageWidth - 8
                && mouseY >= topPos + 174 && mouseY < topPos + imageHeight - 10;
    }

    private void renderMaterials(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        int x = rightPanelLeft() + 10;
        int y = topPos + 176;
        graphics.text(font, Component.translatable(materialSummaryMissing
                        ? "gui.beyond_craftlines.material_summary_missing"
                        : materialSummaryTheoretical ? "gui.beyond_craftlines.summary_total"
                        : "gui.beyond_craftlines.summary_extraction"),
                x, topPos + 157, materialSummaryMissing ? 0xB23A48 : 0x465564, false);
        if (!materialSummaryReady)
        {
            graphics.text(font, Component.translatable("gui.beyond_craftlines.material_summary_loading"),
                    x, y, 0x687784, false);
            return;
        }
        Map<IStackKey<?>, Long> visible = visibleMaterials();
        if (visible.isEmpty())
        {
            graphics.text(font, Component.translatable("gui.beyond_craftlines.material_summary_empty"),
                    x, y, 0x687784, false);
            return;
        }
        List<Map.Entry<IStackKey<?>, Long>> materials = List.copyOf(visible.entrySet());
        materialScroll = Math.max(0, Math.min(materialScroll, materialMaxScroll(materials.size())));
        Map.Entry<IStackKey<?>, Long> hovered = null;
        int columns = materialColumns();
        int end = Math.min(materials.size(), materialScroll + materialPageSize());
        for (int index = materialScroll; index < end; index++)
        {
            var material = materials.get(index);
            int local = index - materialScroll;
            int itemX = x + local % columns * 22;
            int itemY = y + local / columns * 22;
            long available = planningResources.getOrDefault(material.getKey(), 0L);
            int outline = materialSummaryMissing ? 0xFFB23A48
                    : available >= material.getValue() ? 0xFF257A55 : 0xFFB23A48;
            graphics.fill(itemX, itemY, itemX + 18, itemY + 18, 0xFF111923);
            graphics.outline(itemX, itemY, 18, 18, outline);
            material.getKey().getRender().render(graphics, material.getKey(), itemX + 1, itemY + 1);
            material.getKey().getRender().renderAmount(graphics, material.getValue(), itemX + 1, itemY + 1);
            if (mouseX >= itemX && mouseX < itemX + 18 && mouseY >= itemY && mouseY < itemY + 18)
                hovered = material;
        }
        renderMaterialScrollbar(graphics, materials.size());
        if (hovered != null && !pickerOpen())
        {
            long available = planningResources.getOrDefault(hovered.getKey(), 0L);
            List<Component> tooltip = new ArrayList<>(hovered.getKey().getRender().getTooltipLines(
                    hovered.getKey(), available, net.minecraft.world.item.Item.TooltipContext.of(minecraft.level),
                    minecraft.player, minecraft.options.advancedItemTooltips
                            ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL));
            tooltip.add(materialSummaryMissing
                    ? Component.translatable("gui.beyond_craftlines.material_missing_amount", hovered.getValue())
                    .withStyle(ChatFormatting.RED)
                    : Component.translatable(materialSummaryTheoretical
                                    ? "gui.beyond_craftlines.material_total_amount"
                                    : "gui.beyond_craftlines.material_amounts",
                            hovered.getValue(), available).withStyle(available >= hovered.getValue()
                            ? ChatFormatting.GREEN : ChatFormatting.RED));
            graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderMaterialScrollbar(GuiGraphicsExtractor graphics, int materialCount)
    {
        if (materialCount <= materialPageSize()) return;
        int columns = materialColumns();
        int totalRows = (materialCount + columns - 1) / columns;
        int visibleRows = materialRows();
        int firstRow = materialScroll / columns;
        int trackX = leftPos + imageWidth - 10;
        int trackTop = topPos + 176;
        int trackHeight = Math.max(8, visibleRows * 22 - 4);
        int thumbHeight = Math.max(8, trackHeight * visibleRows / totalRows);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int maxFirstRow = Math.max(1, totalRows - visibleRows);
        int thumbY = trackTop + travel * firstRow / maxFirstRow;
        graphics.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, 0xFF293746);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, BD_CYAN);
    }

    private static String compactAmount(long amount)
    {
        if (amount < 1_000) return Long.toString(amount);
        if (amount < 1_000_000) return compactAmount(amount, 1_000, "k");
        if (amount < 1_000_000_000) return compactAmount(amount, 1_000_000, "M");
        if (amount < 1_000_000_000_000L) return compactAmount(amount, 1_000_000_000, "G");
        return compactAmount(amount, 1_000_000_000_000L, "T");
    }

    private static String compactAmount(long amount, long divisor, String suffix)
    {
        long whole = amount / divisor;
        long decimal = amount % divisor * 10 / divisor;
        return decimal == 0 ? whole + suffix : whole + "." + decimal + suffix;
    }

    private void renderTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (minecraft.level == null) return;
        if (treeRoot == null)
        {
            if (!loadingStatus.isBlank())
            {
                graphics.text(font, font.plainSubstrByWidth(
                                loadingStatus, treeRight() - treeLeft() - 10),
                        treeLeft() + 5, treeBottom() - 24, BD_CYAN, false);
                renderLoadingProgress(graphics);
            }
            if (!previewError.isBlank())
                graphics.text(font, font.plainSubstrByWidth(
                                previewError, treeRight() - treeLeft() - 10),
                        treeLeft() + 5, treeBottom() - (loadingStatus.isBlank() ? 24 : 36),
                        0xFFFF6677, true);
            return;
        }
        List<GraphNode> nodes = treeNodes;

        int contentBottom = treeContentBottom();
        graphics.enableScissor(treeLeft() + 1, treeTop() + 1, treeRight() - 1, contentBottom);
        for (GraphNode node : nodes)
        {
            if (node.collapsed || node.jumpTarget != null) continue;
            for (GraphNode child : node.children)
            {
                int x1 = nodeX(node) + 14;
                int y1 = nodeY(node) + 28;
                int x2 = nodeX(child) + 14;
                int y2 = nodeY(child);
                if (!ViewportCulling.intersects(treeLeft(), treeTop(), treeRight(), contentBottom,
                        Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1)) continue;
                int mid = (y1 + y2) / 2;
                line(graphics, x1, y1, x1, mid, BD_BLUE);
                line(graphics, Math.min(x1, x2), mid, Math.max(x1, x2), mid, BD_BLUE);
                line(graphics, x2, mid, x2, y2, BD_CYAN);
            }
        }
        GraphNode hovered = null;
        for (GraphNode node : nodes)
        {
            int x = nodeX(node);
            int y = nodeY(node);
            if (!ViewportCulling.intersects(treeLeft(), treeTop(), treeRight(), contentBottom,
                    x - 1, y - 1, x + 29, y + 29)) continue;
            renderNode(graphics, node, mouseX, mouseY);
            if (!pickerOpen() && mouseX >= x && mouseX < x + 28
                    && mouseY >= y && mouseY < Math.min(y + 28, contentBottom)) hovered = node;
        }
        // Item/resource renderers may defer their vertices. Flush while scissoring is still active,
        // otherwise icons and counts can be emitted later on top of the footer and modal picker.
        graphics.disableScissor();

        if (hovered != null) renderNodeTooltip(graphics, hovered, mouseX, mouseY);

        String zoom = Math.round(treeZoom * 100) + "%";
        graphics.text(font, zoom, treeRight() - font.width(zoom) - 5, treeBottom() - 12, 0x8296A8, false);
        Component resolutionHelp = Component.literal(font.plainSubstrByWidth(
                Component.translatable("gui.beyond_craftlines.tree_resolution_help").getString(),
                Math.max(0, treeRight() - treeLeft() - font.width(zoom) - 16)));
        graphics.text(font, resolutionHelp,
                treeLeft() + 5, treeBottom() - 12, 0x8296A8, false);
        if (!loadingStatus.isBlank())
        {
            graphics.text(font, font.plainSubstrByWidth(loadingStatus, treeRight() - treeLeft() - 10),
                    treeLeft() + 5, treeBottom() - 24, BD_CYAN, false);
            renderLoadingProgress(graphics);
        }
        if (!previewError.isBlank())
            graphics.text(font, font.plainSubstrByWidth(previewError, treeRight() - treeLeft() - 10),
                    treeLeft() + 5, treeBottom() - (loadingStatus.isBlank() ? 24 : 36), 0xFFFF6677, true);
    }

    private void renderLoadingProgress(GuiGraphicsExtractor graphics)
    {
        int current;
        int total;
        if(!JeiCatalystIndex.recipeTypesReady(menu.availableFamilies()))
        {
            current=JeiCatalystIndex.completedRecipeTypes(menu.availableFamilies());
            total=JeiCatalystIndex.totalRecipeTypes(menu.availableFamilies());
        }
        else if (!menu.recipeIndexComplete())
        {
            current = menu.indexedRecipeCandidates();
            total = menu.totalRecipeCandidates();
        }
        else if (planningCatalog == null && planningCatalogBuilder != null)
        {
            current=planningCatalogBuilder.completedRecipes();
            total=planningCatalogBuilder.totalRecipes();
        }
        else return;
        int left = treeLeft() + 5;
        int right = treeRight() - 5;
        int filled = total <= 0 ? right - left : (int) ((long) (right - left) * current / total);
        graphics.fill(left, treeBottom() - 34, right, treeBottom() - 30, 0xFF172638);
        graphics.fill(left, treeBottom() - 34, left + filled, treeBottom() - 30, BD_CYAN);
    }

    private void renderNodeTooltip(GuiGraphicsExtractor graphics, GraphNode node, int mouseX, int mouseY)
    {
        List<Component> lines = node.key instanceof ItemStackKey
                ? new ArrayList<>(Screen.getTooltipFromItem(minecraft, node.stack))
                : new ArrayList<>(node.key.getRender().getTooltipLines(node.key, resourceAvailable(node.key),
                net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
                minecraft.options.advancedItemTooltips
                        ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL));
        if (node.recipe != null)
        {
            lines.add(localizedRecipeType(node.recipe));
        }
        if (node.reusableInput) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.node_reusable").withStyle(ChatFormatting.AQUA));
        else if (node.selfIncrement) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.node_self_increment", node.needed)
                .withStyle(ChatFormatting.GOLD));
        else lines.add(Component.translatable(
                "tooltip.beyond_craftlines.node_need", node.needed).withStyle(ChatFormatting.AQUA));
        if (node.produced > 0) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.node_produced", node.produced, node.crafts)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.beyond_craftlines.node_network",
                resourceAvailable(node.key)).withStyle(ChatFormatting.BLUE));
        if (node.partiallySatisfied) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.stock_partially_satisfied", node.stockUsed,
                node.needed - node.stockUsed).withStyle(node.recipe == null
                ? ChatFormatting.RED : ChatFormatting.GOLD));
        if (node.cycleBlocked) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.cycle_blocked").withStyle(ChatFormatting.RED));
        if (node.stockSatisfied) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.stock_satisfied").withStyle(ChatFormatting.GREEN));
        if (node.jumpTarget != null) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.duplicate_jump", node.jumpTarget.depth)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (node.itemId != null && defaultRecipes.containsKey(node.itemId)) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.default_recipe").withStyle(ChatFormatting.GREEN));
        if (node.parentRecipe != null && node.parentSlots.stream().anyMatch(slot -> defaultIngredients.containsKey(
                new IngredientSlotKey(node.parentRecipe, slot)))) lines.add(Component.translatable(
                "tooltip.beyond_craftlines.default_ingredient").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (node.recipe == null)
        {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
        else
        {
            graphics.setTooltipForNextFrame(font, lines, Optional.<TooltipComponent>of(
                    new RecipePreviewTooltip(singleCraftInputs(node.recipe, node.key),
                            singleCraftOutput(node.recipe, node.key))),
                    node.stack, mouseX, mouseY);
        }
    }

    private List<com.wintercogs.beyonddimensions.api.storage.key.KeyAmount> singleCraftInputs(
            RecipeHolder<?> recipe, IStackKey<?> output)
    {
        LinkedHashMap<IStackKey<?>, Long> amounts = new LinkedHashMap<>();
        for (var ingredient : com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                .ingredientsForOutput(recipe.value(), output))
        {
            var selectedInput = selectedResource(recipe.id().identifier(), ingredient.slot(), ingredient);
            amounts.merge(selectedInput.key(), selectedInput.amount(),
                    com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath::add);
        }
        return amounts.entrySet().stream().map(entry ->
                new com.wintercogs.beyonddimensions.api.storage.key.KeyAmount(
                        entry.getKey(), entry.getValue())).toList();
    }

    private com.wintercogs.beyonddimensions.api.storage.key.KeyAmount singleCraftOutput(
            RecipeHolder<?> recipe, IStackKey<?> output)
    {
        return com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver
                .outputs(recipe.value(), minecraft.level).stream()
                .filter(value -> com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                        .exact(output, value.key())).findFirst()
                .orElse(new com.wintercogs.beyonddimensions.api.storage.key.KeyAmount(output, 1));
    }

    private void rebuildTree()
    { rebuildTree(true); }

    private void rebuildTree(boolean requestPreview)
    {
        if (minecraft.level == null)
        {
            treeRoot = null;
            treeNodes = List.of();
            return;
        }
        IStackKey<?> rootKey = menu.initialTarget();
        Identifier automaticRoot = automaticResourceRecipes.get(
                com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.resolutionKey(rootKey));
        RecipeHolder<?> rootRecipe = selected == null
                ? automaticRoot == null ? null : menu.recipe(automaticRoot)
                : selectedResourceRecipe(rootKey, selected);
        ItemStack rootStack = rootKey instanceof ItemStackKey itemKey
                ? itemKey.getReadOnlyStack().copyWithCount(1) : ItemStack.EMPTY;
        Identifier rootItem = rootKey instanceof ItemStackKey itemKey
                ? BuiltInRegistries.ITEM.getKey(itemKey.getSource()) : null;
        treeRoot = buildTree(rootKey, rootStack, rootItem, rootRecipe,
                null, -1, 0, amountValue(), false, false,
                new HashSet<>(), new TreeStock(planningResources));
        if (CraftlinesConfig.COLLAPSE_DUPLICATE_TREE_RESOURCES.get())
            applyDuplicateTreeReferences(treeRoot, true);
        layout(treeRoot, new int[]{0});
        List<GraphNode> nodes = new ArrayList<>();
        flatten(treeRoot, nodes);
        treeNodes = List.copyOf(nodes);
        if (!treeViewAdjusted)
        {
            treeOffsetX = (treeRight() - treeLeft()) / 2.0 - treeRoot.row * 46 * treeZoom - 14;
            treeOffsetY = 14;
        }
        closeIngredientPicker();
        if (requestPreview) markPreviewDirty();
    }

    private int nodeX(GraphNode node) { return treeLeft() + (int) treeOffsetX + (int) (node.row * 46 * treeZoom); }
    private int nodeY(GraphNode node) { return treeTop() + (int) treeOffsetY + (int) (node.depth * 58 * treeZoom); }

    private void renderNode(GuiGraphicsExtractor graphics, GraphNode node, int mouseX, int mouseY)
    {
        int x = nodeX(node);
        int y = nodeY(node);
        boolean hover = mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28;
        int edge = node.selfIncrement ? BD_ORANGE
                : node.jumpTarget != null ? BD_VIOLET : node.cyclic || node.cycleBlocked ? 0xFFB23A48
                : node.stockSatisfied ? 0xFF39A96B : node.recipe == null ? 0xFFB23A48
                : node.partiallySatisfied ? BD_ORANGE : BD_BLUE;
        graphics.fill(x - 1, y - 1, x + 29, y + 29, PANEL_SHADOW);
        graphics.fill(x, y, x + 28, y + 28, hover ? 0xFF293C50 : 0xFF142131);
        graphics.fill(x, y, x + 28, y + 1, edge);
        graphics.fill(x, y + 27, x + 28, y + 28, edge);
        graphics.fill(x, y, x + 1, y + 28, edge);
        graphics.fill(x + 27, y, x + 28, y + 28, edge);
        int iconX = x + 6;
        int iconY = y + 6;
        node.key.getRender().render(graphics, node.key, iconX, iconY);
        // The item renderer is buffered, while badges are immediate fills. Resolve the icon first
        // so the tag marker and status corners are guaranteed to remain above it.
        if (node.depth == 0) graphics.fill(x + 2, y + 2, x + 6, y + 6, BD_ORANGE);
        if (node.cyclic) graphics.text(font, "!", x + 20, y + 2, 0xFFFF6677, true);
        if (node.selfIncrement) graphics.text(font, "*", x + 20, y + 2, BD_ORANGE, true);
        if (node.jumpTarget != null) graphics.text(font, ">", x + 2, y + 17, 0xFFB78CFF, true);
        if (node.collapsed) graphics.text(font, "+", x + 2, y + 17, 0xFFFFFFFF, true);
        if (node.itemId != null && node.depth > 0 && recipeOverrides.containsKey(node.itemId))
            graphics.fill(x + 2, y + 2, x + 6, y + 6, BD_ORANGE);
        if (node.parentRecipe != null && node.parentSlots.stream().anyMatch(slot -> ingredientOverrides.containsKey(
                new IngredientSlotKey(node.parentRecipe, slot))))
            graphics.fill(x + 2, y + 22, x + 6, y + 26, BD_VIOLET);
        if (node.itemId != null && defaultRecipes.containsKey(node.itemId))
            graphics.fill(x + 22, y + 2, x + 26, y + 6, 0xFF39A96B);
        if (node.parentRecipe != null && node.parentSlots.stream().anyMatch(slot -> defaultIngredients.containsKey(
                new IngredientSlotKey(node.parentRecipe, slot))))
            graphics.fill(x + 22, y + 22, x + 26, y + 26, 0xFFB78CFF);
        if (!node.reusableInput) node.key.getRender().renderAmount(graphics, node.needed, iconX, iconY);
        if(node.ingredientChoices.size()>1||node.fluidContainerAlternative)renderCandidateBadge(graphics,x+2,y+2);
    }

    private static void renderCandidateBadge(GuiGraphicsExtractor graphics, int x, int y)
    {
        graphics.fill(x, y, x + 8, y + 8, 0xC0000000);
        graphics.fill(x + 2, y + 1, x + 3, y + 7, BD_CYAN);
        graphics.fill(x + 5, y + 1, x + 6, y + 7, BD_CYAN);
        graphics.fill(x + 1, y + 2, x + 7, y + 3, BD_CYAN);
        graphics.fill(x + 1, y + 5, x + 7, y + 6, BD_CYAN);
    }

    private static void line(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color)
    {
        graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private GraphNode buildTree(ItemStack stack, RecipeHolder<?> recipe, Identifier parentRecipe,
                                int parentSlot, int depth, long fallbackNeeded, boolean reusableInput,
                                boolean selfIncrementInput, Set<IStackKey<?>> expanding, TreeStock stock)
    {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemStackKey resourceKey = new ItemStackKey(stack.copyWithCount(1));
        return buildTree(resourceKey, stack.copyWithCount(1), itemId, recipe,
                parentRecipe, parentSlot, depth, fallbackNeeded, reusableInput,
                selfIncrementInput, expanding, stock);
    }

    private GraphNode buildTree(IStackKey<?> resourceKey, ItemStack stack, Identifier itemId,
                                RecipeHolder<?> recipe, Identifier parentRecipe,
                                int parentSlot, int depth, long fallbackNeeded, boolean reusableInput,
                                boolean selfIncrementInput,
                                Set<IStackKey<?>> expanding,
                                TreeStock stock)
    {
        boolean cyclic = !selfIncrementInput && expanding.contains(resourceKey);
        // A repeated ancestor is only a visual cycle marker. It is not another material demand and
        // must not consume stock. Every non-cyclic occurrence keeps its local demand; repeated
        // resources are aggregated only after the complete tree has been built.
        long needed = Math.max(1, fallbackNeeded);
        long effectiveNeeded = reusableInput && depth > 0
                ? stock.additionalReusableRequirement(resourceKey, needed) : needed;
        // Whether this node is covered by inventory must come from the same snapshot allocator
        // used for the whole visible tree. The server's extraction total can be lower than the
        // demand when another branch produces an equivalent intermediate; treating that as the
        // node's available stock incorrectly expands a fully stocked node as partially crafted.
        long stockUsed = cyclic || depth <= 0 || effectiveNeeded == 0
                ? 0 : stock.consume(resourceKey, effectiveNeeded);
        long unresolved = effectiveNeeded - stockUsed;
        boolean stockSatisfied = depth > 0 && unresolved == 0;
        GraphNode node = new GraphNode(resourceKey, stack.copyWithCount(1), itemId,
                stockSatisfied || selfIncrementInput ? null : recipe, parentRecipe, parentSlot, depth, needed,
                0, 0);
        node.reusableInput = reusableInput;
        node.selfIncrement = selfIncrementInput;
        node.collapsed = itemId != null && depth > 0 && collapsedNodes.contains(itemId);
        node.stockSatisfied = stockSatisfied;
        node.stockUsed = stockUsed;
        node.partiallySatisfied = stockUsed > 0 && unresolved > 0;
        if (cyclic)
        {
            node.cyclic = true;
            return node;
        }
        if (selfIncrementInput) return node;
        if (node.stockSatisfied) return node;
        if (recipe == null) return node;
        expanding.add(resourceKey);
        List<SelectedTreeInput> selectedInputs = new ArrayList<>();
        List<com.amicbeam.beyondcraftlines.common.crafting.RecipePlan.IngredientSelection> selections =
                new ArrayList<>();
        for (var ingredient : com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                .ingredientsForOutput(recipe.value(), resourceKey))
        {
            int currentSlot = ingredient.slot();
            com.wintercogs.beyonddimensions.api.storage.key.KeyAmount selectedResource =
                    selectedResource(recipe.id().identifier(), currentSlot, ingredient);
            selectedInputs.add(new SelectedTreeInput(currentSlot, ingredient, selectedResource));
            if (selectedResource.key() instanceof ItemStackKey itemKey)
                selections.add(new com.amicbeam.beyondcraftlines.common.crafting.RecipePlan.IngredientSelection(
                        currentSlot, com.amicbeam.beyondcraftlines.common.crafting
                        .IngredientSelectionKey.exact(itemKey)));
        }
        var inputUses=com.amicbeam.beyondcraftlines.common.crafting.SimulatedCrafting
                .inputUses(recipe,minecraft.level,selections);
        var fluidProxies = com.amicbeam.beyondcraftlines.common.crafting.SimulatedCrafting
                .bucketFluidInputs(recipe, minecraft.level, selections);
        for (int i = 0; i < selectedInputs.size(); i++)
        {
            SelectedTreeInput selectedInput = selectedInputs.get(i);
            var proxy = fluidProxies.get(selectedInput.slot());
            String choice=selectedIngredientChoice(recipe.id().identifier(),selectedInput.slot());
            if(proxy!=null&&(choice==null||com.amicbeam.beyondcraftlines.common.crafting.FluidContainerChoice.isProxy(choice)))selectedInputs.set(i,new SelectedTreeInput(
                    selectedInput.slot(), selectedInput.ingredient(), proxy));
        }
        long outputPerCraft = com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver
                .outputs(recipe.value(), minecraft.level).stream()
                .filter(output -> com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                        .exact(resourceKey, output.key())).mapToLong(output -> output.amount())
                .findFirst().orElse(1);
        long seedPerCraft = 0;
        long consumedSeedPerCraft = 0;
        for (SelectedTreeInput selectedInput : selectedInputs)
            if (com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                    .exact(resourceKey, selectedInput.resource().key()))
            {
                var use = com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse.forRecipeSlot(
                        recipe.value(), selectedInput.slot(),
                        selectedInput.slot()<inputUses.length?inputUses[selectedInput.slot()]
                                :com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse.CONSUMED);
                seedPerCraft = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.add(
                        seedPerCraft, selectedInput.resource().amount());
                if (!use.sharedReusable())
                    consumedSeedPerCraft = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.add(
                            consumedSeedPerCraft, selectedInput.resource().amount());
            }
        var shape = com.amicbeam.beyondcraftlines.common.crafting.SelfIncrementRecipe.analyze(
                Math.max(1, outputPerCraft), seedPerCraft, consumedSeedPerCraft, unresolved);
        long recipeCrafts = shape.crafts();
        node.crafts = recipeCrafts;
        node.produced = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.multiply(
                recipeCrafts, shape.netOutputPerCraft());
        List<TreeInput> inputs = new ArrayList<>();
        for (SelectedTreeInput selectedInput : selectedInputs)
        {
            int currentSlot = selectedInput.slot();
            var ingredient = selectedInput.ingredient();
            var selectedResource = selectedInput.resource();
            IStackKey<?> inputKey = selectedResource.key();
            var use = com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse.forRecipeSlot(
                    recipe.value(),currentSlot,currentSlot<inputUses.length?inputUses[currentSlot]
                            :com.amicbeam.beyondcraftlines.common.crafting.VirtualInputUse.CONSUMED);
            boolean reusableSlot = use.sharedReusable();
            boolean selfInput = shape.selfIncrement() && com.amicbeam.beyondcraftlines.common.crafting
                    .StackKeyMatch.exact(resourceKey, inputKey);
            long totalAmount = selfInput ? selectedResource.amount()
                    : use.requiredAmount(recipeCrafts,inputKey,selectedResource.amount(),planningResources);
            TreeInput merged = inputKey instanceof ItemStackKey ? inputs.stream()
                    .filter(input -> com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                            .exact(input.key, inputKey))
                    .findFirst().orElse(null)
                    : null;
            if (merged != null)
            {
                merged.add(currentSlot, totalAmount, ingredient, reusableSlot);
                continue;
            }
            inputs.add(new TreeInput(inputKey, totalAmount, currentSlot, ingredient, reusableSlot, selfInput));
        }
        for (TreeInput inputGroup : inputs)
        {
            IStackKey<?> inputKey = inputGroup.key;
            TreeInputSlot firstSlot = inputGroup.slots.getFirst();
            GraphNode child;
            if (inputKey instanceof ItemStackKey itemKey)
            {
                ItemStack input = itemKey.getReadOnlyStack().copyWithCount(
                        (int) Math.min(Integer.MAX_VALUE, inputGroup.amount));
                Identifier inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
                RecipeHolder<?> childRecipe = selectedResourceRecipe(
                        inputKey, menu.recipeForResourceOutput(inputKey));
                child = buildTree(input, childRecipe, recipe.id().identifier(), firstSlot.slot(), depth + 1,
                        inputGroup.amount, inputGroup.reusableOnly, inputGroup.selfIncrement, expanding, stock);
            }
            else
            {
                RecipeHolder<?> childRecipe = selectedResourceRecipe(
                        inputKey, menu.recipeForResourceOutput(inputKey));
                child = buildTree(inputKey, ItemStack.EMPTY, null, childRecipe,
                        recipe.id().identifier(), firstSlot.slot(), depth + 1, inputGroup.amount,
                        inputGroup.reusableOnly, inputGroup.selfIncrement, expanding, stock);
            }
            child.setIngredientChoices(firstSlot.ingredient());
            child.fluidContainerAlternative|=fluidProxies.containsKey(firstSlot.slot());
            for (int i = 1; i < inputGroup.slots.size(); i++)
            {
                TreeInputSlot slot = inputGroup.slots.get(i);
                child.mergeSlot(slot.slot(), slot.ingredient());
            }
            if (child.cyclic) node.cycleBlocked = true;
            node.children.add(child);
        }
        expanding.remove(resourceKey);
        return node;
    }

    private com.wintercogs.beyonddimensions.api.storage.key.KeyAmount selectedResource(
            Identifier recipe, int slot,
            com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.ResourceIngredient ingredient)
    {
        String selectedId=selectedIngredientChoice(recipe,slot);
        if(com.amicbeam.beyondcraftlines.common.crafting.FluidContainerChoice.isProxy(selectedId)){
            var fluid=ingredient.candidates().stream().filter(candidate->candidate.key() instanceof
                    com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey).findFirst().orElse(null);
            if(fluid!=null)return fluid;Identifier container=com.amicbeam.beyondcraftlines.common.crafting.FluidContainerChoice.itemOrNull(selectedId);selectedId=container==null?selectedId:container.toString();}
        if(selectedId!=null)for(var candidate:ingredient.candidates())if(com.amicbeam.beyondcraftlines.common.crafting.IngredientSelectionKey.matches(selectedId,candidate.key()))return candidate;
        if (ingredient.hasOnlyItemCandidates())
        {
            return ingredient.candidates().getFirst();
        }
        return ingredient.candidates().stream().sorted(java.util.Comparator
                .<com.wintercogs.beyonddimensions.api.storage.key.KeyAmount>comparingLong(
                        value -> resourceAvailable(value.key())).reversed()
                .thenComparing(value -> com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                        .sortKey(value.key()))).findFirst().orElseThrow();
    }

    private String selectedIngredientChoice(Identifier recipe,int slot){String selected=ingredientOverrides.get(new IngredientSlotKey(recipe,slot));
        if(selected==null)selected=automaticIngredients.get(new IngredientSlotKey(recipe,slot));if(selected==null)selected=defaultIngredients.get(new IngredientSlotKey(recipe,slot));return selected;}

    private long resourceAvailable(IStackKey<?> requested)
    {
        long result = 0;
        for (var entry : planningResources.entrySet())
            if (com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch.exact(
                    requested, entry.getKey()))
            {
                long amount = Math.max(0, entry.getValue());
                result = result > Long.MAX_VALUE - amount ? Long.MAX_VALUE : result + amount;
            }
        return result;
    }

    private NodeMetric nodeMetric(IStackKey<?> requested)
    {
        NodeMetric exact = nodeMetrics.get(requested);
        if (exact != null) return exact;
        for (var entry : nodeMetrics.entrySet())
            if (com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch.exact(
                    requested, entry.getKey())) return entry.getValue();
        return null;
    }

    private RecipeHolder<?> selectedRecipe(Identifier output, RecipeHolder<?> fallback)
    {
        List<RecipeHolder<?>> candidates = menu.recipesForOutput(output);
        if (candidates.isEmpty()) return fallback;
        Identifier selectedId = recipeOverrides.get(output);
        if (selectedId == null) selectedId = automaticRecipes.get(output);
        if (selectedId == null) selectedId = defaultRecipes.get(output);
        if (selectedId != null)
            for (RecipeHolder<?> candidate : candidates) if (candidate.id().identifier().equals(selectedId)) return candidate;
        return fallback != null && candidates.stream().anyMatch(candidate -> candidate.id().identifier().equals(fallback.id().identifier()))
                ? fallback : candidates.getFirst();
    }

    private RecipeHolder<?> selectedResourceRecipe(IStackKey<?> output, RecipeHolder<?> fallback)
    {
        List<RecipeHolder<?>> candidates = menu.recipesForResourceOutput(output);
        String token = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.resolutionKey(output);
        Identifier manualId = resourceRecipeOverrides.get(token);
        Identifier automaticId = automaticResourceRecipes.get(token);
        Identifier selectedId = manualId;
        if (selectedId == null) selectedId = automaticId;
        if (selectedId == null) selectedId = defaultResourceRecipes.get(token);
        if (selectedId != null)
            for (RecipeHolder<?> candidate : candidates) if (candidate.id().identifier().equals(selectedId)) return candidate;
        if (manualId == null && automaticId != null)
        {
            RecipeHolder<?> planned = menu.recipe(automaticId);
            if (planned != null) return planned;
        }
        NodeMetric metric = nodeMetric(output);
        Identifier plannedId = metric == null ? null : metric.recipe();
        if (manualId == null && plannedId != null && menu.recipeProduces(plannedId,
                com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(output)))
        {
            RecipeHolder<?> planned = menu.recipe(plannedId);
            if (planned != null) return planned;
        }
        if (candidates.isEmpty()) return fallback;
        return fallback != null && candidates.stream().anyMatch(candidate -> candidate.id().identifier().equals(fallback.id().identifier()))
                ? fallback : candidates.getFirst();
    }

    private Identifier outputId(RecipeHolder<?> holder)
    { var output = com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver.primary(holder.value(), minecraft.level);
      return output != null && output.key() instanceof ItemStackKey item
              ? BuiltInRegistries.ITEM.getKey(item.getSource()) : Identifier.withDefaultNamespace("air"); }

    private Map<String, Identifier> genericRecipeOverrides()
    {
        LinkedHashMap<String, Identifier> result = new LinkedHashMap<>();
        recipeOverrides.forEach((output, recipe) -> result.put(itemToken(output), recipe));
        result.putAll(resourceRecipeOverrides);
        com.amicbeam.beyondcraftlines.common.crafting.RootRecipeOverridePolicy.putInitialFallback(
                result, menu.targetToken(), selected == null ? null : selected.id().identifier());
        return Map.copyOf(result);
    }

    private static String itemToken(Identifier item)
    {
        return com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.sortKey(
                new ItemStackKey(new ItemStack(BuiltInRegistries.ITEM.getValue(item))));
    }

    private Identifier itemOutputForToken(String token)
    {
        return menu.itemOutputForToken(token);
    }

    private static void layout(GraphNode node, int[] nextRow)
    {
        if (node.children.isEmpty() || node.collapsed || node.jumpTarget != null) node.row = nextRow[0]++;
        else
        {
            for (GraphNode child : node.children) layout(child, nextRow);
            node.row = (node.children.getFirst().row + node.children.getLast().row) / 2.0;
        }
    }

    private static void flatten(GraphNode node, List<GraphNode> nodes)
    {
        nodes.add(node);
        if (!node.collapsed && node.jumpTarget == null)
            for (GraphNode child : node.children) flatten(child, nodes);
    }

    private static void applyDuplicateTreeReferences(GraphNode root, boolean aggregateLocalQuantities)
    {
        List<GraphNode> all = new ArrayList<>();
        collectExpandedNodes(root, all);
        Map<IStackKey<?>, List<GraphNode>> groups = new LinkedHashMap<>();
        for (GraphNode node : all)
            if (!node.selfIncrement)
            groups.computeIfAbsent(displayIdentity(node.key), ignored -> new ArrayList<>()).add(node);
        Map<IStackKey<?>, GraphNode> canonical = new LinkedHashMap<>();
        for (var entry : groups.entrySet())
        {
            GraphNode selected = entry.getValue().stream()
                    // Keep the occurrence that still owns an expansion tree as the visible one.
                    // Otherwise a shallower stock-satisfied leaf could absorb a partially
                    // satisfied duplicate and hide the latter's genuine missing-material branch.
                    .min(java.util.Comparator.comparing((GraphNode node) -> node.children.isEmpty())
                            .thenComparing(node -> node.stockSatisfied)
                            .thenComparingInt(node -> node.depth)).orElseThrow();
            canonical.put(entry.getKey(), selected);
        }
        for (var entry : groups.entrySet())
        {
            GraphNode target = canonical.get(entry.getKey());
            for (GraphNode node : entry.getValue())
            {
                if (target == node) continue;
                if (aggregateLocalQuantities) mergeDisplayedTotals(target, node);
                node.jumpTarget = target;
            }
        }
        for (int pass = 0; pass < all.size(); pass++)
        {
            boolean changed = false;
            List<GraphNode> visible = new ArrayList<>();
            collectVisibleNodes(root, visible);
            Set<GraphNode> visibleSet = new HashSet<>(visible);
            Map<IStackKey<?>, GraphNode> visibleByIdentity = new LinkedHashMap<>();
            for (GraphNode node : visible)
                visibleByIdentity.putIfAbsent(displayIdentity(node.key), node);
            for (var entry : canonical.entrySet())
            {
                if (visibleSet.contains(entry.getValue())) continue;
                GraphNode replacement = visibleByIdentity.get(entry.getKey());
                if (replacement == null) continue;
                entry.setValue(replacement);
                for (GraphNode node : groups.get(entry.getKey()))
                    node.jumpTarget = node == replacement ? null : replacement;
                changed = true;
            }
            if (!changed) break;
        }
    }

    private static void mergeDisplayedTotals(GraphNode target, GraphNode source)
    {
        target.fluidContainerAlternative|=source.fluidContainerAlternative;
        boolean reusable = target.reusableInput && source.reusableInput;
        target.reusableInput = reusable;
        if (reusable)
        {
            long outputPerCraft = target.crafts > 0 ? Math.max(1, target.produced / target.crafts)
                    : source.crafts > 0 ? Math.max(1, source.produced / source.crafts) : 1;
            target.needed = Math.max(target.needed, source.needed);
            target.stockUsed = Math.min(target.needed,
                    com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.add(
                            target.stockUsed, source.stockUsed));
            long unresolved = target.needed - target.stockUsed;
            boolean craftable = target.recipe != null || source.recipe != null;
            target.crafts = unresolved == 0 || !craftable ? 0
                    : com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.ceilDiv(
                            unresolved, outputPerCraft);
            target.produced = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.multiply(
                    target.crafts, outputPerCraft);
            target.stockSatisfied = target.depth > 0 && unresolved == 0;
            target.partiallySatisfied = target.stockUsed > 0 && target.stockUsed < target.needed;
            return;
        }
        long outputPerCraft = target.crafts > 0 ? Math.max(1, target.produced / target.crafts)
                : source.crafts > 0 ? Math.max(1, source.produced / source.crafts) : 1;
        target.needed = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.add(
                target.needed, source.needed);
        target.stockUsed = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.add(
                target.stockUsed, source.stockUsed);
        long unresolved = Math.max(0, target.needed - target.stockUsed);
        target.crafts = unresolved == 0 ? 0
                : com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.ceilDiv(
                        unresolved, outputPerCraft);
        target.produced = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.multiply(
                target.crafts, outputPerCraft);
        target.stockSatisfied = target.depth > 0 && target.stockUsed >= target.needed;
        target.partiallySatisfied = target.stockUsed > 0 && target.stockUsed < target.needed;
    }

    private static void collectExpandedNodes(GraphNode node, List<GraphNode> nodes)
    {
        nodes.add(node);
        if (!node.collapsed) for (GraphNode child : node.children) collectExpandedNodes(child, nodes);
    }

    private static void collectVisibleNodes(GraphNode node, List<GraphNode> nodes)
    {
        nodes.add(node);
        if (!node.collapsed && node.jumpTarget == null)
            for (GraphNode child : node.children) collectVisibleNodes(child, nodes);
    }

    private static IStackKey<?> displayIdentity(IStackKey<?> key) { return key; }

    private boolean openIngredientPicker(GraphNode node)
    {
        List<ItemStack> candidates = ingredientCandidates(node);
        if(candidates.size()<2&&!node.fluidContainerAlternative)return false;
        recipePickerNode = null;
        recipePickerRecipes = List.of();
        ingredientPickerNode = node;
        ingredientPickerItems = candidates;
        int selectedIndex = 0;
        for (int i = 0; i < candidates.size(); i++)
            if(com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch.exact(new ItemStackKey(candidates.get(i)),node.key))
            { selectedIndex = i; break; }
        ingredientPickerPage = selectedIndex / PICKER_PAGE_SIZE;
        positionPicker(node);
        return true;
    }

    private boolean openRecipePicker(GraphNode node)
    {
        if (node.stockSatisfied) return false;
        List<RecipeHolder<?>> candidates = menu.recipesForResourceOutput(node.key);
        if (candidates.isEmpty() || node.recipe != null && candidates.size() < 2) return false;
        ingredientPickerNode = null;
        ingredientPickerItems = List.of();
        recipePickerNode = node;
        recipePickerRecipes = candidates;
        int selectedIndex = 0;
        if (node.recipe != null)
            for (int i = 0; i < candidates.size(); i++)
                if (candidates.get(i).id().identifier().equals(node.recipe.id().identifier())) { selectedIndex = i; break; }
        ingredientPickerPage = selectedIndex / PICKER_PAGE_SIZE;
        positionPicker(node);
        return true;
    }

    private void positionPicker(GraphNode node)
    {
        ingredientPickerX = Math.max(treeLeft() + 4,
                Math.min(nodeX(node) - PICKER_WIDTH / 2 + 14, treeRight() - PICKER_WIDTH - 4));
        ingredientPickerY = Math.max(treeTop() + 4,
                Math.min(nodeY(node) + 32, treeBottom() - PICKER_HEIGHT - 4));
    }

    private List<ItemStack> ingredientCandidates(GraphNode node)
    {
        return node.ingredientChoices.entrySet().stream().sorted(java.util.Comparator
                .<Map.Entry<String, ItemStack>>comparingLong(entry -> resourceAvailable(new ItemStackKey(entry.getValue()))).reversed()
                .thenComparing(entry -> entry.getKey().toString()))
                .map(Map.Entry::getValue).toList();
    }

    private void applyIngredientChoice(GraphNode node, String selectedItem)
    {
        if (node.parentRecipe == null) return;
        for (int slot : node.parentSlots)
            ingredientOverrides.put(new IngredientSlotKey(node.parentRecipe, slot), selectedItem);
        boolean saved = ClientPlannerPreferences.setIngredients(
                node.parentRecipe, node.parentSlots, selectedItem);
        if (saved)
            for (int slot : node.parentSlots)
                defaultIngredients.put(new IngredientSlotKey(node.parentRecipe, slot), selectedItem);
        showPreferenceSaveResult(saved);
        closeIngredientPicker();
        rebuildTree();
    }

    private void applyRecipeChoice(GraphNode node, RecipeHolder<?> recipe)
    {
        if (node.depth == 0) selected = recipe;
        String token = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.resolutionKey(node.key);
        resourceRecipeOverrides.put(token, recipe.id().identifier());
        if (node.itemId != null) recipeOverrides.put(node.itemId, recipe.id().identifier());
        boolean saved = ClientPlannerPreferences.setRecipe(token, recipe.id().identifier());
        if (saved)
        {
            defaultResourceRecipes.put(token, recipe.id().identifier());
            if (node.itemId != null) defaultRecipes.put(node.itemId, recipe.id().identifier());
        }
        showPreferenceSaveResult(saved);
        closeIngredientPicker();
        rebuildTree();
    }

    private void showPreferenceSaveResult(boolean saved)
    {
        if (minecraft.player != null) minecraft.player.sendOverlayMessage(Component.translatable(saved
                ? "message.beyond_craftlines.planner_default_saved"
                : "error.beyond_craftlines.client_planner_preference_save_failed"));
    }

    private boolean pickerHasPreference()
    {
        if (recipePickerNode != null)
        {
            String token = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                    .resolutionKey(recipePickerNode.key);
            return defaultResourceRecipes.containsKey(token)
                    || recipePickerNode.itemId != null && defaultRecipes.containsKey(recipePickerNode.itemId);
        }
        return ingredientPickerNode != null && ingredientPickerNode.parentRecipe != null
                && ingredientPickerNode.parentSlots.stream().anyMatch(slot -> defaultIngredients.containsKey(
                new IngredientSlotKey(ingredientPickerNode.parentRecipe, slot)));
    }

    private int forgetPreferenceButtonWidth()
    { return font.width(Component.translatable("gui.beyond_craftlines.forget_preference")) + 8; }

    private boolean overForgetPreferenceButton(double mouseX, double mouseY)
    {
        if (!pickerHasPreference()) return false;
        int width = forgetPreferenceButtonWidth();
        int x = ingredientPickerX + PICKER_WIDTH - width - 3;
        return mouseX >= x && mouseX < x + width
                && mouseY >= ingredientPickerY + 3 && mouseY < ingredientPickerY + 15;
    }

    private void forgetPickerPreference()
    {
        boolean saved;
        if (recipePickerNode != null)
        {
            String token = com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                    .resolutionKey(recipePickerNode.key);
            saved = ClientPlannerPreferences.clearRecipe(token, recipePickerNode.itemId);
            if (saved)
            {
                defaultResourceRecipes.remove(token);
                resourceRecipeOverrides.remove(token);
                if (recipePickerNode.itemId != null)
                {
                    defaultRecipes.remove(recipePickerNode.itemId);
                    recipeOverrides.remove(recipePickerNode.itemId);
                }
            }
        }
        else if (ingredientPickerNode != null && ingredientPickerNode.parentRecipe != null)
        {
            saved = ClientPlannerPreferences.setIngredients(
                    ingredientPickerNode.parentRecipe, ingredientPickerNode.parentSlots, null);
            if (saved) for (int slot : ingredientPickerNode.parentSlots)
            {
                IngredientSlotKey key = new IngredientSlotKey(ingredientPickerNode.parentRecipe, slot);
                defaultIngredients.remove(key);
                ingredientOverrides.remove(key);
            }
        }
        else return;
        if (minecraft.player != null) minecraft.player.sendOverlayMessage(Component.translatable(saved
                ? "message.beyond_craftlines.planner_default_cleared"
                : "error.beyond_craftlines.client_planner_preference_save_failed"));
        if (!saved) return;
        closeIngredientPicker();
        rebuildTree();
    }

    private boolean clickIngredientPicker(double mouseX, double mouseY, int button)
    {
        if (mouseX < ingredientPickerX || mouseX >= ingredientPickerX + PICKER_WIDTH
                || mouseY < ingredientPickerY || mouseY >= ingredientPickerY + PICKER_HEIGHT) return false;
        if (button != 0) return true;
        if (overForgetPreferenceButton(mouseX, mouseY))
        {
            forgetPickerPreference();
            return true;
        }
        int gridX = (int) mouseX - ingredientPickerX - 4;
        int gridY = (int) mouseY - ingredientPickerY - PICKER_HEADER_HEIGHT;
        if (gridX >= 0 && gridX < PICKER_COLUMNS * 20 && gridY >= 0 && gridY < PICKER_ROWS * 20)
        {
            int index = ingredientPickerPage * PICKER_PAGE_SIZE
                    + gridY / 20 * PICKER_COLUMNS + gridX / 20;
            if (recipePickerNode != null && index < recipePickerRecipes.size())
            {
                applyRecipeChoice(recipePickerNode, recipePickerRecipes.get(index));
            }
            else if (ingredientPickerNode != null && index < ingredientPickerItems.size())
            {
                ItemStack selectedStack = ingredientPickerItems.get(index);
                applyIngredientChoice(ingredientPickerNode,
                        com.amicbeam.beyondcraftlines.common.crafting.IngredientSelectionKey.exact(new ItemStackKey(selectedStack)));
            }
            return true;
        }
        int pages = pickerPages();
        if (mouseY >= ingredientPickerY + PICKER_HEIGHT - 15)
        {
            if (mouseX < ingredientPickerX + 28 && ingredientPickerPage > 0) ingredientPickerPage--;
            else if (mouseX >= ingredientPickerX + PICKER_WIDTH - 28
                    && ingredientPickerPage + 1 < pages) ingredientPickerPage++;
        }
        return true;
    }

    private void renderIngredientPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (!pickerOpen()) return;
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        try
        {
            graphics.fill(ingredientPickerX - 2, ingredientPickerY - 2,
                    ingredientPickerX + PICKER_WIDTH + 2, ingredientPickerY + PICKER_HEIGHT + 2, PANEL_SHADOW);
            graphics.fill(ingredientPickerX, ingredientPickerY,
                    ingredientPickerX + PICKER_WIDTH, ingredientPickerY + PICKER_HEIGHT, 0xFF202A36);
            graphics.text(font, Component.translatable(recipePickerNode == null
                            ? "gui.beyond_craftlines.choose_tag_item" : "gui.beyond_craftlines.choose_recipe"),
                    ingredientPickerX + 5, ingredientPickerY + 5, 0xFFD8F3FF, false);
            if (pickerHasPreference())
            {
                Component label = Component.translatable("gui.beyond_craftlines.forget_preference");
                int width = forgetPreferenceButtonWidth();
                int x = ingredientPickerX + PICKER_WIDTH - width - 3;
                boolean hover = mouseX >= x && mouseX < x + width
                        && mouseY >= ingredientPickerY + 3 && mouseY < ingredientPickerY + 15;
                graphics.fill(x, ingredientPickerY + 3, x + width, ingredientPickerY + 15,
                        hover ? 0xFF8E3E49 : 0xFF66313A);
                graphics.outline(x, ingredientPickerY + 3, width, 12,
                        hover ? 0xFFFFA0AA : 0xFFB86A73);
                graphics.text(font, label, x + 4, ingredientPickerY + 5, 0xFFFFFFFF, false);
            }

            int first = ingredientPickerPage * PICKER_PAGE_SIZE;
            int end = Math.min(pickerSize(), first + PICKER_PAGE_SIZE);
            ItemStack hovered = ItemStack.EMPTY;
            IStackKey<?> hoveredKey = null;
            RecipeHolder<?> hoveredRecipe = null;
            for (int index = first; index < end; index++)
            {
                int local = index - first;
                int x = ingredientPickerX + 4 + local % PICKER_COLUMNS * 20;
                int y = ingredientPickerY + PICKER_HEADER_HEIGHT + local / PICKER_COLUMNS * 20;
                RecipeHolder<?> candidateRecipe = recipePickerNode == null ? null : recipePickerRecipes.get(index);
                ItemStack stack = candidateRecipe == null ? ingredientPickerItems.get(index) : ItemStack.EMPTY;
                IStackKey<?> candidateKey = candidateRecipe == null ? null : recipePickerNode.key;
                boolean selected = candidateRecipe == null
                        ? BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ingredientPickerNode.itemId)
                        : recipePickerNode.recipe != null && candidateRecipe.id().identifier().equals(recipePickerNode.recipe.id().identifier());
                boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                graphics.fill(x, y, x + 18, y + 18, hover ? 0xFF38536D : 0xFF111923);
                graphics.outline(x, y, 18, 18, selected ? BD_CYAN : 0xFF526273);
                if (candidateKey == null) graphics.item(stack, x + 1, y + 1);
                else candidateKey.getRender().render(graphics, candidateKey, x + 1, y + 1);
                if (hover)
                {
                    hovered = stack;
                    hoveredKey = candidateKey;
                    hoveredRecipe = candidateRecipe;
                }
            }
            // Resolve modal item batches before drawing its footer and tooltip above them.
            int pages = pickerPages();
            int footerY = ingredientPickerY + PICKER_HEIGHT - 13;
            graphics.text(font, "<", ingredientPickerX + 7, footerY,
                    ingredientPickerPage > 0 ? 0xFFFFFFFF : 0xFF687784, false);
            graphics.centeredText(font, (ingredientPickerPage + 1) + "/" + pages,
                    ingredientPickerX + PICKER_WIDTH / 2, footerY, 0xFFB8C8D8);
            graphics.text(font, ">", ingredientPickerX + PICKER_WIDTH - 13, footerY,
                    ingredientPickerPage + 1 < pages ? 0xFFFFFFFF : 0xFF687784, false);
            if (hoveredRecipe != null) renderRecipeCandidateTooltip(
                    graphics, hoveredRecipe, hoveredKey, mouseX, mouseY);
            else if (!hovered.isEmpty()) graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        }
        finally
        {
            graphics.pose().popMatrix();
        }
    }

    private boolean overIngredientPicker(double mouseX, double mouseY)
    {
        return pickerOpen() && mouseX >= ingredientPickerX
                && mouseX < ingredientPickerX + PICKER_WIDTH && mouseY >= ingredientPickerY
                && mouseY < ingredientPickerY + PICKER_HEIGHT;
    }

    private void closeIngredientPicker()
    {
        ingredientPickerNode = null;
        ingredientPickerItems = List.of();
        recipePickerNode = null;
        recipePickerRecipes = List.of();
        ingredientPickerPage = 0;
    }

    private int pickerSize()
    {
        return recipePickerNode == null ? ingredientPickerItems.size() : recipePickerRecipes.size();
    }

    private int pickerPages()
    {
        return Math.max(1, (pickerSize() + PICKER_PAGE_SIZE - 1) / PICKER_PAGE_SIZE);
    }

    private void renderRecipeCandidateTooltip(GuiGraphicsExtractor graphics, RecipeHolder<?> recipe, IStackKey<?> output,
                                              int mouseX, int mouseY)
    {
        List<Component> lines = List.of(
                output.getRender().getDisplayName(output),
                localizedRecipeType(recipe));
        List<com.wintercogs.beyonddimensions.api.storage.key.KeyAmount> inputs =
                com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                        .ingredientsForOutput(recipe.value(), output)
                        .stream().map(ingredient -> ingredient.candidates().getFirst()).toList();
        graphics.setTooltipForNextFrame(font, lines, Optional.<TooltipComponent>of(
                new RecipePreviewTooltip(inputs,
                        com.amicbeam.beyondcraftlines.common.crafting.RecipeOutputResolver
                                .outputs(recipe.value(), minecraft.level).stream()
                                .filter(value -> com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                                        .exact(output, value.key())).findFirst()
                        .orElse(new com.wintercogs.beyonddimensions.api.storage.key.KeyAmount(output, 1)))),
                output instanceof ItemStackKey itemKey ? itemKey.getReadOnlyStack() : ItemStack.EMPTY,
                mouseX, mouseY);
    }

    private static Component localizedRecipeType(RecipeHolder<?> recipe)
    {
        String family = com.amicbeam.beyondcraftlines.common.crafting.RecipePlanningService.family(recipe);
        Identifier type = recipe.value().getType() == null ? Identifier.tryParse(family)
                : BuiltInRegistries.RECIPE_TYPE.getKey(recipe.value().getType());
        if (type == null) return Component.translatable("tooltip.beyond_craftlines.recipe_type",
                family == null ? "unknown" : family).withStyle(ChatFormatting.GRAY);
        return JeiCatalystIndex.recipeTypeTitle(type)
                .<Component>map(title -> Component.translatable("tooltip.beyond_craftlines.recipe_type_localized",
                        type, title).withStyle(ChatFormatting.GRAY))
                .orElseGet(() -> Component.translatable("tooltip.beyond_craftlines.recipe_type", type)
                        .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (pickerOpen())
        {
            if (clickIngredientPicker(mouseX, mouseY, button)) return true;
            closeIngredientPicker();
            return true;
        }
        if (button == 0 && overTree(mouseX, mouseY))
        {
            GraphNode node = nodeAt(mouseX, mouseY);
            if (node != null)
            {
                if (overCandidateBadge(node, mouseX, mouseY) && openIngredientPicker(node)) return true;
                if (node.jumpTarget != null)
                {
                    centerTreeOn(node.jumpTarget);
                    return true;
                }
                if (com.amicbeam.beyondcraftlines.client.integration.jei.CraftlinesJeiPlugin
                        .showRecipesFor(node.key)) return true;
            }
        }
        if (button == 2 && overTree(mouseX, mouseY))
        {
            GraphNode node = nodeAt(mouseX, mouseY);
            if (node != null && node.itemId != null && node.depth > 0 && !node.children.isEmpty())
            {
                if (!collapsedNodes.add(node.itemId)) collapsedNodes.remove(node.itemId);
                rebuildTree(false);
                return true;
            }
        }
        if (button == 1 && overTree(mouseX, mouseY))
        {
            GraphNode node = nodeAt(mouseX, mouseY);
            if (node != null)
            {
                if (node.stockSatisfied) return true;
                if (event.hasShiftDown()) openIngredientPicker(node);
                else openRecipePicker(node);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void centerTreeOn(GraphNode node)
    {
        treeViewAdjusted = true;
        treeOffsetX = (treeRight() - treeLeft()) / 2.0 - node.row * 46 * treeZoom - 14;
        treeOffsetY = (treeContentBottom() - treeTop()) / 2.0 - node.depth * 58 * treeZoom - 14;
    }

    private GraphNode nodeAt(double mouseX, double mouseY)
    {
        if (pickerOpen() || mouseX < treeLeft() || mouseX >= treeRight()
                || mouseY < treeTop() || mouseY >= treeContentBottom()) return null;
        for (GraphNode node : treeNodes)
        {
            int x = nodeX(node);
            int y = nodeY(node);
            if (mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28) return node;
        }
        return null;
    }

    private boolean overCandidateBadge(GraphNode node, double mouseX, double mouseY)
    {
        if(node.ingredientChoices.size()<2&&!node.fluidContainerAlternative)return false;
        int x = nodeX(node) + 2;
        int y = nodeY(node) + 2;
        return mouseX >= x && mouseX < x + 8 && mouseY >= y && mouseY < y + 8;
    }

    private void loadClientPreferences()
    {
        ClientPlannerPreferences.Snapshot snapshot = ClientPlannerPreferences.load();
        outputDestination = snapshot.outputDestination();
        if (!(menu.initialTarget() instanceof ItemStackKey))
            outputDestination = OrderOutputDestination.NETWORK;
        updateOutputDestinationButton();
        defaultRecipes.clear();
        defaultResourceRecipes.clear();
        for (var entry : snapshot.recipes().entrySet())
        {
            Identifier recipe = entry.getValue();
            String token = entry.getKey();
            Identifier legacyOutput = token.indexOf('|') < 0 ? Identifier.tryParse(token) : null;
            if (legacyOutput != null) token = itemToken(legacyOutput);
            String finalToken = token;
            boolean valid = menu.recipeProduces(recipe, finalToken);
            if (!valid) continue;
            defaultResourceRecipes.put(token, recipe);
            Identifier output = itemOutputForToken(token);
            if (output != null) defaultRecipes.put(output, recipe);
        }
        defaultIngredients.clear();
        for (var entry : snapshot.ingredients().entrySet())
        {
            int separator = entry.getKey().lastIndexOf('#');
            if (separator < 1) continue;
            Identifier recipeId = Identifier.tryParse(entry.getKey().substring(0, separator));
            int slot;
            try { slot = Integer.parseInt(entry.getKey().substring(separator + 1)); }
            catch (NumberFormatException ignored) { continue; }
            String item = entry.getValue();
            RecipeHolder<?> recipe = recipeId == null ? null : menu.recipe(recipeId);
            if (recipe == null || item == null || item.isBlank() || slot < 0) continue;
            // Candidate membership is checked lazily by selectedResource while that one tree node expands.
            defaultIngredients.put(new IngredientSlotKey(recipeId, slot), item);
        }
        rebuildTree();
    }

    private void markPreviewDirty()
    { markPreviewDirty(CraftlinesConfig.RECIPE_PREVIEW_DELAY_TICKS.get(), false); }

    private void markAmountPreviewDirty()
    { markPreviewDirty(CraftlinesConfig.AMOUNT_PREVIEW_DELAY_TICKS.get(), true); }

    private void markPreviewDirty(int delayTicks, boolean amountOnly)
    {
        cancelPlanningTask();
        previewDirty = true;
        previewDelay = 0;
        previewDelayTicks = Math.max(1, delayTicks);
        amountOnlyPreviewChange = amountOnly;
        previewNonce++;
        submitWhenReady = false;
        proposalReady = false;
        planningOutcome=com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.SEARCHING;
        clearDisplayMetrics();
        if (orderButton != null) orderButton.active = canQueueOrder();
    }

    private void clearDisplayMetrics()
    {
        materialSummaryReady = false;
        materialSummaryMissing = false;
        materialSummaryTheoretical = false;
        missingMaterials.clear();
        extractionMaterials.clear();
        nodeMetrics.clear();
        materialScroll = 0;
    }

    private void clearPendingPreviewMetrics()
    {
        pendingMissingMaterials.clear();
        pendingExtractionMaterials.clear();
        pendingNodeMetrics.clear();
    }

    private void showMissingMaterials(Map<IStackKey<?>, Long> missing)
    {
        clearDisplayMetrics();
        com.amicbeam.beyondcraftlines.common.crafting.MissingMaterialDisplay
                .excludingFinalOutput(missing, menu.initialTarget()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(
                        com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver::sortKey)))
                .forEach(entry -> missingMaterials.put(entry.getKey(), entry.getValue()));
        materialSummaryMissing = true;
        materialSummaryReady = true;
    }

    private void requestPlanPreview()
    {
        previewDirty = false;
        previewDelay = 0;
        if (minecraft.level == null) return;
        IStackKey<?> target = menu.initialTarget();
        long nonce = previewNonce;
        boolean preferAutomaticChoices = amountOnlyPreviewChange;
        amountOnlyPreviewChange = false;
        previewNextPage = 0;
        snapshotNextPage = 0;
        previewError = "";
        long snapshotAge = planningSnapshotValid
                ? minecraft.level.getGameTime() - planningSnapshotCapturedAt : Long.MAX_VALUE;
        if (planningSnapshotValid && (preferAutomaticChoices || snapshotAge <= 20))
        {
            loadingStatus=planningText();
            startClientPlanning(nonce, target, amountValue(), planningSnapshotRevision, planningRecipeEpoch,
                    planningMaxDepth, planningMaxNodes, Map.copyOf(planningResources),
                    genericRecipeOverrides(), Map.copyOf(ingredientOverrides), preferAutomaticChoices,
                    preferAutomaticChoices && snapshotAge > 20);
            return;
        }
        requestedSnapshotPrefersAutomaticChoices = preferAutomaticChoices;
        planningSnapshotValid = false;
        planningResources.clear();
        loadingStatus = Component.translatable("gui.beyond_craftlines.loading_snapshot").getString();
        ClientPacketDistributor.sendToServer(new RequestPlanningSnapshotPayload(nonce, menu.targetToken()));
    }

    private void receivePlanningSnapshot(PlanningSnapshotPayload snapshot)
    {
        if (snapshot.nonce() != previewNonce
                || !snapshot.itemId().equals(menu.targetToken())) return;
        var header = snapshot.header();
        if (!header.status().success())
        {
            planningResources.clear();
            planningSnapshotValid = false;
            snapshotNextPage = 0;
            loadingStatus = "";
            previewError = localizedPlanningError(header.status().error());
            return;
        }
        if (header.pageCount() < 1 || header.pageIndex() != snapshotNextPage
                || header.pageIndex() < 0 || header.pageIndex() >= header.pageCount())
        {
            planningResources.clear();
            planningSnapshotValid = false;
            snapshotNextPage = 0;
            loadingStatus = "";
            previewError = localizedPlanningMessage("error.beyond_craftlines.planning_snapshot_sequence");
            return;
        }
        if (header.pageIndex() == 0)
        {
            planningResources.clear();
        }
        for (PlanningSnapshotPayload.Entry entry : snapshot.entries())
        {
            if (entry.key() == null || entry.key().isEmpty() || entry.amount() < 1) continue;
            planningResources.merge(entry.key(), entry.amount(), Long::sum);
        }
        snapshotNextPage++;
        loadingStatus = Component.translatable("gui.beyond_craftlines.loading_snapshot_pages",
                snapshotNextPage, header.pageCount()).getString();
        if (snapshotNextPage < header.pageCount()) return;
        snapshotNextPage = 0;
        planningSnapshotValid = true;
        planningSnapshotCapturedAt = minecraft.level.getGameTime();
        planningSnapshotRevision = header.stockRevision();
        planningRecipeEpoch = header.recipeEpoch();
        planningMaxDepth = header.limits().maxDepth();
        planningMaxNodes = header.limits().maxNodes();
        // The stock snapshot is authoritative enough to collapse satisfied branches immediately;
        // do not leave the pre-snapshot expanded tree visible while proposal planning runs.
        rebuildTree(false);
        startClientPlanning(snapshot.nonce(), menu.initialTarget(), amountValue(), header.stockRevision(),
                header.recipeEpoch(), header.limits().maxDepth(), header.limits().maxNodes(),
                Map.copyOf(planningResources), genericRecipeOverrides(), Map.copyOf(ingredientOverrides),
                requestedSnapshotPrefersAutomaticChoices, false);
    }

    private void startClientPlanning(long nonce, IStackKey<?> target, long count,
                                     long stockRevision, long recipeEpoch, int maxDepth, int maxNodes,
                                     Map<IStackKey<?>, Long> stock,
                                     Map<String, Identifier> manualRecipes,
                                     Map<IngredientSlotKey, String> manualIngredients,
                                     boolean preferAutomaticChoices, boolean refreshSnapshotIfMissing)
    {
        LinkedHashMap<String, Identifier> preferredRecipes = new LinkedHashMap<>(defaultResourceRecipes);
        defaultRecipes.forEach((output, recipe) -> preferredRecipes.put(itemToken(output), recipe));
        if (preferAutomaticChoices)
        {
            preferredRecipes.putAll(automaticResourceRecipes);
            automaticRecipes.forEach((output, recipe) -> preferredRecipes.put(itemToken(output), recipe));
        }
        preferredRecipes.putAll(manualRecipes);
        Map<ClientRecipePlanner.IngredientKey, String> preferredIngredients = new LinkedHashMap<>();
        defaultIngredients.forEach((key, value) -> preferredIngredients.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        if (preferAutomaticChoices) automaticIngredients.forEach((key, value) -> preferredIngredients.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        manualIngredients.forEach((key, value) -> preferredIngredients.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        LinkedHashMap<String, Identifier> defaultRecipesOnly = new LinkedHashMap<>(defaultResourceRecipes);
        defaultRecipes.forEach((output, recipe) -> defaultRecipesOnly.put(itemToken(output), recipe));
        defaultRecipesOnly.putAll(manualRecipes);
        Map<ClientRecipePlanner.IngredientKey, String> defaultIngredientsOnly = new LinkedHashMap<>();
        defaultIngredients.forEach((key, value) -> defaultIngredientsOnly.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        manualIngredients.forEach((key, value) -> defaultIngredientsOnly.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        Map<ClientRecipePlanner.IngredientKey, String> forcedIngredients = new LinkedHashMap<>();
        manualIngredients.forEach((key, value) -> forcedIngredients.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        Map<String, Identifier> fixedTreeRecipes = visibleTreeRecipes();
        Map<ClientRecipePlanner.IngredientKey, String> fixedTreeIngredients = visibleTreeIngredients();
        preferredRecipes.putAll(fixedTreeRecipes);
        preferredRecipes.putAll(manualRecipes);
        preferredIngredients.putAll(fixedTreeIngredients);
        manualIngredients.forEach((key, value) -> preferredIngredients.put(
                new ClientRecipePlanner.IngredientKey(key.recipe(), key.slot()), value));
        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                "{} client plan visible-tree choices recipes={} ingredients={}",
                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                fixedTreeRecipes.size(), fixedTreeIngredients.size());
        boolean hasDefaults = !defaultResourceRecipes.isEmpty() || !defaultRecipes.isEmpty()
                || !defaultIngredients.isEmpty();
        boolean hasAutomaticChoices = preferAutomaticChoices && (!automaticResourceRecipes.isEmpty()
                || !automaticRecipes.isEmpty() || !automaticIngredients.isEmpty());
        cancelPlanningTask();
        loadingStatus=planningText();
        long generation = planningGeneration;
        planningTask = PLANNING_EXECUTOR.submit(() -> {
            ClientRecipePlanner.Proposal proposal = null;
            RuntimeException failure = null;
            long searchDeadline = System.nanoTime() + ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS;
            boolean optimalSearch=false;
            try { proposal = ClientRecipePlanner.plan(planningCatalog,
                    stock, target, count, preferredRecipes, preferredIngredients, maxDepth, maxNodes,
                    ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS,optimalSearch); }
            catch (RuntimeException exception) { failure = exception; }
            boolean searchExhausted = proposal != null && proposal.searchExhausted();
            long fallbackSearchNanos = searchDeadline - System.nanoTime();
            if (!refreshSnapshotIfMissing && hasAutomaticChoices && fallbackSearchNanos > 0
                    && (proposal == null || !proposal.craftable()))
            {
                try
                {
                    ClientRecipePlanner.Proposal fallback = ClientRecipePlanner.plan(planningCatalog,
                            stock, target, count, defaultRecipesOnly, defaultIngredientsOnly, maxDepth, maxNodes,
                            fallbackSearchNanos,optimalSearch);
                    searchExhausted |= fallback.searchExhausted();
                    if (proposal == null || missingAmount(fallback.missing()) <= missingAmount(proposal.missing()))
                    {
                        proposal = fallback;
                        failure = null;
                    }
                }
                catch (RuntimeException ignored) {}
                fallbackSearchNanos = searchDeadline - System.nanoTime();
            }
            if (!refreshSnapshotIfMissing && hasDefaults && fallbackSearchNanos > 0
                    && (proposal == null || !proposal.craftable()))
            {
                try
                {
                    ClientRecipePlanner.Proposal fallback = ClientRecipePlanner.plan(planningCatalog,
                            stock, target, count, manualRecipes, forcedIngredients, maxDepth, maxNodes,
                            fallbackSearchNanos,optimalSearch);
                    searchExhausted |= fallback.searchExhausted();
                    if (proposal == null || missingAmount(fallback.missing()) <= missingAmount(proposal.missing()))
                    {
                        proposal = fallback;
                        failure = null;
                    }
                }
                catch (RuntimeException ignored) {}
            }
            if (!refreshSnapshotIfMissing && proposal != null && !proposal.craftable()
                    && searchExhausted&&(fallbackSearchNanos=searchDeadline-System.nanoTime())>0)
            {
                try
                {
                    // Candidate optimization is bounded, but the already-visible tree must still be
                    // validated deterministically. This preserves a craftable current choice instead
                    // of replacing it with the incomplete branch that happened to hit the budget.
                    proposal = ClientRecipePlanner.plan(planningCatalog, stock, target, count,
                            fixedTreeRecipes, fixedTreeIngredients, maxDepth, maxNodes,
                            fallbackSearchNanos,false);
                    failure = null;
                }
                catch (RuntimeException exception) { failure = exception; }
            }
            ClientRecipePlanner.Proposal completed = proposal;
            RuntimeException completedFailure = failure;
            minecraft.execute(() -> {
                    if (generation != planningGeneration) return;
                    planningTask = null;
                    if (nonce != previewNonce
                            || !com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                            .exact(target, menu.initialTarget())) return;
                    loadingStatus = "";
                    if (completedFailure != null)
                    {
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                                "{} client plan failed nonce={} target={} error={}",
                                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                                nonce, com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target),
                                completedFailure.toString(), completedFailure);
                        if (completedFailure instanceof CancellationException
                                || "client planning cancelled".equals(completedFailure.getMessage())) return;
                        if (refreshSnapshotIfMissing)
                        {
                            refreshSnapshotAfterFastAmountPlan(nonce, preferAutomaticChoices);
                            return;
                        }
                        submitWhenReady = false;
                        planningOutcome=com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.RUNTIME_UNAVAILABLE;
                        if (orderButton != null) orderButton.active = false;
                        previewError = localizedPlanningError(completedFailure.getMessage());
                        return;
                    }
                    automaticRecipes.clear();
                    automaticResourceRecipes.clear();
                    completed.recipes().forEach((output, recipe) -> {
                        automaticResourceRecipes.put(output, recipe);
                        Identifier item = itemOutputForToken(output);
                        if (item != null) automaticRecipes.put(item, recipe);
                    });
                    automaticIngredients.clear();
                    completed.ingredients().forEach((key, value) -> automaticIngredients.put(
                            new IngredientSlotKey(key.recipe(), key.slot()), value));
                    if (!completed.craftable())
                    {
                        planningOutcome=completed.outcome();
                        com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.warn(
                                "{} client plan missing nonce={} recipes={} ingredients={} missing={}",
                                com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                                nonce, completed.recipes().size(), completed.ingredients().size(),
                                completed.missing().size());
                        if (refreshSnapshotIfMissing)
                        {
                            refreshSnapshotAfterFastAmountPlan(nonce, preferAutomaticChoices);
                            return;
                        }
                        submitWhenReady = false;
                        if (orderButton != null) orderButton.active = false;
                        showMissingMaterials(completed.missing());
                        previewError=planningOutcomeMessage(planningOutcome,completed.missing());
                        rebuildTree(false);
                        return;
                    }
                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                            "{} client plan ready nonce={} recipes={} ingredients={} extraction={} target={}",
                            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                            nonce, completed.recipes().size(), completed.ingredients().size(),
                            completed.extraction().size(),
                            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));
                    completed.recipes().forEach((key, recipe) ->
                            com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info(
                                    "{} client plan recipe key={} recipe={}",
                                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,
                                    com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.token(key), recipe));
                    uploadProposal(nonce, target, count, stockRevision, recipeEpoch, completed);
                    clearDisplayMetrics();
                    completed.extraction().entrySet().stream().filter(entry -> !com.amicbeam.beyondcraftlines
                                    .common.crafting.StackKeyMatch.exact(target, entry.getKey()))
                            .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(
                                    com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver::sortKey)))
                            .forEach(entry -> extractionMaterials.put(entry.getKey(), entry.getValue()));
                    materialSummaryReady = true;
                    proposalReady = true;
                    planningOutcome=com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.READY;
                    previewError = "";
                    if (orderButton != null) orderButton.active = true;
                    rebuildTree(false);
                    boolean submitWasQueued=submitWhenReady;
                    if(submitWasQueued)submit();else if(CraftlinesConfig.ENABLE_OPTIMAL_RECIPE_SEARCH.get())startOptimalPlanning(generation,nonce,target,count,stockRevision,recipeEpoch,maxDepth,maxNodes,stock,preferredRecipes,preferredIngredients);
                });
        });
    }

    private void startOptimalPlanning(long generation,long nonce,IStackKey<?> target,long count,long stockRevision,long recipeEpoch,int maxDepth,int maxNodes,Map<IStackKey<?>,Long> stock,Map<String,Identifier> preferredRecipes,Map<ClientRecipePlanner.IngredientKey,String> preferredIngredients)
    {
        optimizationTask=OPTIMIZATION_EXECUTOR.submit(()->{ClientRecipePlanner.Proposal improved;try{improved=ClientRecipePlanner.plan(planningCatalog,stock,target,count,preferredRecipes,preferredIngredients,maxDepth,maxNodes,ClientRecipePlanner.SEARCH_TIME_LIMIT_NANOS,true);}catch(RuntimeException ignored){minecraft.execute(()->{if(generation==planningGeneration)optimizationTask=null;});return;}minecraft.execute(()->{if(generation!=planningGeneration||nonce!=previewNonce)return;optimizationTask=null;if(!improved.craftable())return;automaticRecipes.clear();automaticResourceRecipes.clear();improved.recipes().forEach((output,recipe)->{automaticResourceRecipes.put(output,recipe);Identifier item=itemOutputForToken(output);if(item!=null)automaticRecipes.put(item,recipe);});automaticIngredients.clear();improved.ingredients().forEach((key,value)->automaticIngredients.put(new IngredientSlotKey(key.recipe(),key.slot()),value));uploadProposal(nonce,target,count,stockRevision,recipeEpoch,improved);rebuildTree(false);com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.LOGGER.info("{} client optimal plan ready nonce={} recipes={} ingredients={} target={}",com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.PREFIX,nonce,improved.recipes().size(),improved.ingredients().size(),com.amicbeam.beyondcraftlines.common.crafting.OrderDiagnostics.resource(target));});});
    }

    private Map<String, Identifier> visibleTreeRecipes()
    {
        LinkedHashMap<String, Identifier> result = new LinkedHashMap<>();
        for (GraphNode node : treeNodes)
            if (node.recipe != null)
                result.put(com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver
                        .resolutionKey(node.key), node.recipe.id().identifier());
        return Map.copyOf(result);
    }

    private Map<ClientRecipePlanner.IngredientKey, String> visibleTreeIngredients()
    {
        LinkedHashMap<ClientRecipePlanner.IngredientKey, String> result = new LinkedHashMap<>();
        for (GraphNode node : treeNodes)
            if (node.parentRecipe != null && node.itemId != null)
                for (int slot : node.parentSlots)
                    result.put(new ClientRecipePlanner.IngredientKey(node.parentRecipe, slot),com.amicbeam.beyondcraftlines.common.crafting.IngredientSelectionKey.exact(node.key));
        return Map.copyOf(result);
    }

    private void refreshSnapshotAfterFastAmountPlan(long nonce, boolean preferAutomaticChoices)
    {
        planningSnapshotValid = false;
        requestedSnapshotPrefersAutomaticChoices = preferAutomaticChoices;
        planningResources.clear();
        loadingStatus = Component.translatable("gui.beyond_craftlines.loading_snapshot").getString();
        ClientPacketDistributor.sendToServer(new RequestPlanningSnapshotPayload(nonce, menu.targetToken()));
    }

    private static long missingAmount(Map<IStackKey<?>, Long> missing)
    {
        long total = 0;
        for (long amount : missing.values())
            total = com.amicbeam.beyondcraftlines.common.crafting.SaturatingLongMath.add(total, amount);
        return total;
    }

    private String formatMissing(Map<IStackKey<?>, Long> missing)
    {
        String details = missing.entrySet().stream().limit(6).map(entry -> {
            String name = entry.getKey().getRender().getDisplayName(entry.getKey()).getString();
            return name + " ×" + entry.getKey().getRender().getCountText(entry.getValue());
        }).collect(java.util.stream.Collectors.joining("、"));
        if (missing.size() > 6) details += "…";
        return Component.translatable("gui.beyond_craftlines.missing", details).getString();
    }
    private String planningOutcomeMessage(com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome outcome,Map<IStackKey<?>,Long> missing)
    {return switch(outcome){case NO_RECIPE->Component.translatable("error.beyond_craftlines.planning_no_recipe").getString();case CYCLE->Component.translatable("error.beyond_craftlines.planning_cycle_only").getString();case BUDGET_EXHAUSTED->Component.translatable("error.beyond_craftlines.planning_budget_exhausted").getString();default->formatMissing(missing);};}

    private void cancelPlanningTask()
    {
        planningGeneration++;
        Future<?> task = planningTask;
        planningTask = null;
        if (task != null) task.cancel(true);
        Future<?> optimization=optimizationTask;
        optimizationTask=null;
        if(optimization!=null)optimization.cancel(true);
        PLANNING_EXECUTOR.purge();
        OPTIMIZATION_EXECUTOR.purge();
    }

    private static ScheduledThreadPoolExecutor planningExecutor()
    {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable,
                    "beyond-craftlines-planner-" + PLANNER_THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private void uploadProposal(long nonce, IStackKey<?> target, long count, long stockRevision,
                                long recipeEpoch, ClientRecipePlanner.Proposal proposal)
    {
        java.util.Set<Identifier> selectedVirtualRecipes = java.util.Set.copyOf(proposal.recipes().values());
        var virtualPages = com.amicbeam.beyondcraftlines.common.network.VirtualRecipeUploadPayload
                .pages(nonce, selectedVirtualRecipes);
        if (virtualPages.size() > 64)
        {
            previewError = localizedPlanningMessage("error.beyond_craftlines.planning_upload_limit");
            return;
        }
        virtualPages.forEach(page -> ClientPacketDistributor.sendToServer(page));
        List<SubmitOrderPayload.RecipeChoice> recipes = proposal.recipes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SubmitOrderPayload.RecipeChoice(
                        entry.getKey(), entry.getValue().toString())).toList();
        List<SubmitOrderPayload.IngredientChoice> ingredients = proposal.ingredients().entrySet().stream()
                .sorted(java.util.Comparator.comparing((Map.Entry<ClientRecipePlanner.IngredientKey, String> entry)
                                -> entry.getKey().recipe().toString()).thenComparingInt(entry -> entry.getKey().slot()))
                .map(entry -> new SubmitOrderPayload.IngredientChoice(entry.getKey().recipe().toString(),
                        entry.getKey().slot(), entry.getValue())).toList();
        int pageCount = Math.max(1, Math.max((recipes.size() + 255) / 256, (ingredients.size() + 255) / 256));
        if (pageCount > 64)
        {
            previewError = localizedPlanningMessage("error.beyond_craftlines.planning_upload_limit");
            return;
        }
        proposalStockRevision = stockRevision;
        proposalRecipeEpoch = recipeEpoch;
        for (int page = 0; page < pageCount; page++)
        {
            int recipeFrom = Math.min(recipes.size(), page * 256);
            int ingredientFrom = Math.min(ingredients.size(), page * 256);
            ClientPacketDistributor.sendToServer(new PlanProposalUploadPayload(nonce, menu.targetToken(),
                    new PlanProposalUploadPayload.Header(count, stockRevision, recipeEpoch, page, pageCount),
                    recipes.subList(recipeFrom, Math.min(recipes.size(), recipeFrom + 256)),
                    ingredients.subList(ingredientFrom, Math.min(ingredients.size(), ingredientFrom + 256))));
        }
    }

    private void receivePlanPreview(PlanPreviewPayload preview)
    {
        if (preview.nonce() != previewNonce || !preview.itemId().equals(menu.targetToken()))
            return;
        if (!preview.success())
        {
            loadingStatus = "";
            clearPendingPreviewMetrics();
            proposalReady = false;
            clearDisplayMetrics();
            if (orderButton != null) orderButton.active = false;
            automaticRecipes.clear();
            automaticResourceRecipes.clear();
            automaticIngredients.clear();
            previewNextPage = 0;
            previewError = localizedPlanningError(preview.error());
            rebuildTree(false);
            planningOutcome=com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.byId(preview.failureKind());
            if(planningOutcome==com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.STALE)
            {
                planningSnapshotValid = false;
                markPreviewDirty();
            }
            return;
        }
        if (preview.pageCount() < 1 || preview.pageIndex() < 0 || preview.pageIndex() >= preview.pageCount()
                || preview.pageIndex() != previewNextPage)
        {
            loadingStatus = "";
            clearPendingPreviewMetrics();
            proposalReady = false;
            if (orderButton != null) orderButton.active = false;
            automaticRecipes.clear();
            automaticResourceRecipes.clear();
            automaticIngredients.clear();
            clearDisplayMetrics();
            previewNextPage = 0;
            previewError = localizedPlanningMessage("error.beyond_craftlines.planning_preview_sequence");
            rebuildTree(false);
            return;
        }
        if (preview.pageIndex() == 0)
        {
            automaticRecipes.clear();
            automaticResourceRecipes.clear();
            automaticIngredients.clear();
            clearPendingPreviewMetrics();
            previewError = "";
        }
        for (SubmitOrderPayload.RecipeChoice choice : preview.recipeChoices())
        {
            Identifier output = itemOutputForToken(choice.output());
            Identifier recipe = Identifier.tryParse(choice.recipe());
            if (recipe != null) automaticResourceRecipes.put(choice.output(), recipe);
            if (output != null && recipe != null) automaticRecipes.put(output, recipe);
        }
        for (SubmitOrderPayload.IngredientChoice choice : preview.ingredientChoices())
        {
            Identifier recipe = Identifier.tryParse(choice.recipe());
            if(recipe!=null&&choice.item()!=null&&!choice.item().isBlank()&&choice.slot()>=0)
                automaticIngredients.put(new IngredientSlotKey(recipe,choice.slot()),choice.item());
        }
        for (PlanPreviewPayload.DisplayEntry entry : preview.displayEntries())
        {
            if (entry.key() == null || entry.amount() < 1) continue;
            switch (entry.kind())
            {
                case "M" -> {
                    if (!com.amicbeam.beyondcraftlines.common.crafting.MissingMaterialDisplay
                            .isFinalOutput(menu.initialTarget(), entry.key()))
                        pendingMissingMaterials.put(entry.key(), entry.amount());
                }
                case "E" -> pendingExtractionMaterials.put(entry.key(), entry.amount());
                case "N" -> pendingNodeMetrics.put(entry.key(), new NodeMetric(Identifier.tryParse(entry.recipe()),
                        entry.amount(), entry.produced(), entry.crafts()));
            }
        }
        previewNextPage++;
        loadingStatus = Component.translatable("gui.beyond_craftlines.validating_plan_pages",
                previewNextPage, preview.pageCount()).getString();
        if (previewNextPage >= preview.pageCount())
        {
            previewNextPage = 0;
            loadingStatus = "";
            clearDisplayMetrics();
            missingMaterials.putAll(pendingMissingMaterials);
            extractionMaterials.putAll(pendingExtractionMaterials);
            nodeMetrics.putAll(pendingNodeMetrics);
            clearPendingPreviewMetrics();
            planningOutcome=com.amicbeam.beyondcraftlines.common.crafting.PlanningOutcome.byId(preview.failureKind());
            boolean missing=!planningOutcome.craftable()||!missingMaterials.isEmpty();
            proposalReady=planningOutcome.craftable()&&missingMaterials.isEmpty();
            materialSummaryReady = true;
            materialSummaryMissing = missing;
            previewError=missing?planningOutcomeMessage(planningOutcome,missingMaterials):"";
            if (orderButton != null) orderButton.active = !missing;
            rebuildTree(false);
        }
    }

    private String localizedPlanningError(String error)
    {
        var translated=com.amicbeam.beyondcraftlines.common.network.OrderMenuError.decode(error);
        if(translated!=null)return Component.translatable(translated.translationKey(),translated.arguments().toArray()).getString();
        if (error == null || error.isBlank())
            return localizedPlanningMessage("error.beyond_craftlines.planning_failed");
        if (error.equals("client planning node budget exhausted"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_node_budget");
        if (error.equals("client planning time budget exhausted"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_time_budget");
        if (error.equals("invalid planning snapshot page sequence"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_snapshot_sequence");
        if (error.equals("invalid plan preview page sequence"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_preview_sequence");
        if (error.equals("client proposal exceeds the upload limit")
                || error.equals("planning stock snapshot exceeds the transfer limit"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_upload_limit");
        if (error.equals("network not found") || error.equals("network unavailable"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_network_unavailable");
        if (error.equals("target is unavailable") || error.equals("target is not available in this order menu"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_target_unavailable");
        if (error.equals("planning snapshot changed; refreshing") || error.contains("refresh the preview"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_stale");
        if (error.startsWith("missing:") || error.startsWith("required ingredients changed:"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_materials_changed");
        if (error.contains("recipe resolution") || error.startsWith("selected recipe is unavailable")
                || error.startsWith("recipe has no supported output"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_recipe_invalid");
        if (error.contains("ingredient resolution") || error.contains("ingredient proposal")
                || error.startsWith("ingredient cannot be enumerated")
                || error.startsWith("selected ingredient is invalid"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_ingredient_invalid");
        if (error.contains("proposal") || error.contains("page sequence"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_protocol_invalid");
        if (error.equals("recipe tree is too complex; planning budget exceeded"))
            return localizedPlanningMessage("error.beyond_craftlines.planning_server_budget");
        return localizedPlanningMessage("error.beyond_craftlines.planning_failed");
    }

    private String localizedPlanningMessage(String key)
    { return Component.translatable(key).getString(); }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (overTree(mouseX, mouseY) && (button == 0 || button == 2))
        {
            closeIngredientPicker();
            treeViewAdjusted = true;
            treeOffsetX += dragX;
            treeOffsetY += dragY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (overMaterials(mouseX, mouseY) && visibleMaterials().size() > materialPageSize())
        {
            int columns = materialColumns();
            materialScroll = Math.max(0, Math.min(materialMaxScroll(visibleMaterials().size()),
                    materialScroll + (scrollY < 0 ? columns : -columns)));
            return true;
        }
        if (overIngredientPicker(mouseX, mouseY))
        {
            int pages = pickerPages();
            ingredientPickerPage = Math.max(0, Math.min(pages - 1,
                    ingredientPickerPage + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        if (overTree(mouseX, mouseY))
        {
            closeIngredientPicker();
            treeViewAdjusted = true;
            double oldZoom = treeZoom;
            treeZoom = Math.max(MIN_TREE_ZOOM, Math.min(MAX_TREE_ZOOM, treeZoom + scrollY * 0.1));
            double anchorX = mouseX - treeLeft() - treeOffsetX;
            double anchorY = mouseY - treeTop() - treeOffsetY;
            treeOffsetX -= anchorX * (treeZoom / oldZoom - 1.0);
            treeOffsetY -= anchorY * (treeZoom / oldZoom - 1.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
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

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        // This menu has no inventory slots. The inherited title and inventory label overlapped
        // the custom screen labels, producing the visible ghost text.
    }

    @Override public void removed()
    {
        cancelPlanningTask();
        super.removed();
        NetworkAmountPayload.clientReceiver = (ignoredId, ignoredAmount) -> {};
        PlanPreviewPayload.clientReceiver = ignored -> {};
        PlanningSnapshotPayload.clientReceiver = ignored -> {};
    }

    private static final class GraphNode
    {
        private final IStackKey<?> key;
        private final ItemStack stack;
        private final Identifier itemId;
        private RecipeHolder<?> recipe;
        private final Identifier parentRecipe;
        private final List<Integer> parentSlots = new ArrayList<>();
        private final LinkedHashMap<String, ItemStack> ingredientChoices = new LinkedHashMap<>();
        private final int depth;
        private final List<GraphNode> children = new ArrayList<>();
        private boolean cyclic;
        private boolean cycleBlocked;
        private boolean collapsed;
        private boolean stockSatisfied;
        private boolean partiallySatisfied;
        private boolean reusableInput;
        private boolean fluidContainerAlternative;
        private boolean selfIncrement;
        private long stockUsed;
        private GraphNode jumpTarget;
        private long needed;
        private long produced;
        private long crafts;
        private double row;

        private GraphNode(IStackKey<?> key, ItemStack stack, Identifier itemId, RecipeHolder<?> recipe,
                          Identifier parentRecipe, int parentSlot, int depth,
                          long needed, long produced, long crafts)
        {
            this.key = key;
            this.stack = stack;
            this.itemId = itemId;
            this.recipe = recipe;
            this.parentRecipe = parentRecipe;
            if (parentSlot >= 0) this.parentSlots.add(parentSlot);
            this.depth = depth;
            this.needed = needed;
            this.produced = produced;
            this.crafts = crafts;
        }

        private void setIngredientChoices(
                com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.ResourceIngredient ingredient)
        {
            ingredientChoices.clear();
            for (var candidate : ingredient.candidates())
                if (candidate.key() instanceof ItemStackKey itemKey)
                    ingredientChoices.putIfAbsent(com.amicbeam.beyondcraftlines.common.crafting.IngredientSelectionKey.exact(itemKey),
                            itemKey.getReadOnlyStack().copyWithCount((int) Math.min(
                                    Integer.MAX_VALUE, candidate.amount())));
        }

        private void mergeSlot(int slot,
                               com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.ResourceIngredient ingredient)
        {
            parentSlots.add(slot);
            Set<String> allowed = new HashSet<>();
            for (var candidate : ingredient.candidates())
                if (candidate.key() instanceof ItemStackKey itemKey)
                    allowed.add(com.amicbeam.beyondcraftlines.common.crafting.IngredientSelectionKey.exact(itemKey));
            ingredientChoices.keySet().removeIf(item -> !allowed.contains(item));
        }
    }

    private static final class TreeInput
    {
        private final IStackKey<?> key;
        private long amount;
        private boolean reusableOnly;
        private boolean selfIncrement;
        private final List<TreeInputSlot> slots = new ArrayList<>();

        private TreeInput(IStackKey<?> key, long amount, int slot,
                          com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.ResourceIngredient ingredient,
                          boolean reusable, boolean selfIncrement)
        {
            this.key = key;
            this.amount = Math.max(1, amount);
            this.reusableOnly = reusable;
            this.selfIncrement = selfIncrement;
            slots.add(new TreeInputSlot(slot, ingredient));
        }

        private void add(int slot, long added,
                         com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.ResourceIngredient ingredient,
                         boolean reusable)
        {
            long positive = Math.max(1, added);
            amount = amount > Long.MAX_VALUE - positive ? Long.MAX_VALUE : amount + positive;
            reusableOnly &= reusable;
            slots.add(new TreeInputSlot(slot, ingredient));
        }
    }

    private record TreeInputSlot(int slot,
            com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.ResourceIngredient ingredient) {}

    private record SelectedTreeInput(int slot,
            com.amicbeam.beyondcraftlines.common.crafting.RecipeResourceResolver.ResourceIngredient ingredient,
            com.wintercogs.beyonddimensions.api.storage.key.KeyAmount resource) {}

    private record IngredientSlotKey(Identifier recipe, int slot) {}
    private record NodeMetric(Identifier recipe, long needed, long produced, long crafts) {}

    private static final class TreeStock
    {
        private final LinkedHashMap<IStackKey<?>, Long> remaining = new LinkedHashMap<>();
        private final LinkedHashMap<IStackKey<?>, Long> reusableRequirements = new LinkedHashMap<>();

        private TreeStock(Map<IStackKey<?>, Long> source)
        { source.forEach((key, amount) -> remaining.put(key, Math.max(0, amount))); }

        private long additionalReusableRequirement(IStackKey<?> requested, long amount)
        {
            long positive = Math.max(1, amount);
            for (var entry : reusableRequirements.entrySet())
            {
                if (!com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                        .exact(requested, entry.getKey())) continue;
                long previous = entry.getValue();
                if (positive <= previous) return 0;
                entry.setValue(positive);
                return positive - previous;
            }
            reusableRequirements.put(requested, positive);
            return positive;
        }

        private long consume(IStackKey<?> requested, long amount)
        {
            long left = Math.max(0, amount);
            long consumed = 0;
            for (var entry : remaining.entrySet())
            {
                if (left == 0) break;
                if (!com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch
                        .exact(requested, entry.getKey())) continue;
                long take = Math.min(left, entry.getValue());
                if (take == 0) continue;
                entry.setValue(entry.getValue() - take);
                consumed += take;
                left -= take;
            }
            return consumed;
        }
    }

}
