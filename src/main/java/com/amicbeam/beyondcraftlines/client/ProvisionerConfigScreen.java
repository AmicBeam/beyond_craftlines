package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.common.data.ResourceFilter;
import com.amicbeam.beyondcraftlines.common.network.ConfigureResourceFilterPayload;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.network.ConfigureProvisionerPayload;
import com.amicbeam.beyondcraftlines.common.network.ConfigureBindingPriorityPayload;
import com.amicbeam.beyondcraftlines.common.network.ReturnProvisionerContentPayload;
import com.amicbeam.beyondcraftlines.common.network.ConfigureProvisionerDeliveryStrategyPayload;
import com.amicbeam.beyondcraftlines.common.runtime.ProvisionerDeliveryStrategy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public final class ProvisionerConfigScreen extends AbstractContainerScreen<ProvisionerConfigMenu>
{
    private static final int ROWS = 4;
    private static final int MANUAL_ROWS = 5;
    private static final int MANUAL_ROW_HEIGHT = 12;
    private static final int MAX_GROUPS = 16;
    private static final int MAX_GROUP_COLUMNS = 4;
    private static final int GROUP_HEIGHT = 16;
    private static final int GROUP_GAP_X = 3;
    private static final int GROUP_GAP_Y = 2;
    private static final int ENTRY_GAP = 4;
    private static final int CONTENT_TOP = 44;
    private List<ResourceLocation> candidates;
    private final boolean manualFallback;
    private final Set<ResourceLocation> selected;
    private final Map<ResourceLocation, Set<String>> availableGroups;
    private final Map<ResourceLocation, Set<String>> selectedGroups;
    private final List<Button> rows = new ArrayList<>();
    private final List<List<Button>> groupRows = new ArrayList<>();
    private final List<Button> filterControls = new ArrayList<>();
    private boolean filterEditing;
    private int page;
    private Button previous;
    private Button next;
    private Button returnAll;
    private Button deliveryStrategyButton;
    private EditBox search;
    private boolean dropdownOpen;
    private int dropdownScroll;
    private int priority;
    private ProvisionerDeliveryStrategy deliveryStrategy;
    private int deliveryStrategySyncGrace;

    public ProvisionerConfigScreen(ProvisionerConfigMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        manualFallback = menu.allowsManualRecipeSelection();
        candidates = loadCandidates();
        selected = new LinkedHashSet<>(menu.selected());
        availableGroups = menu.availableGroups();
        selectedGroups = new HashMap<>();
        menu.selectedGroups().forEach((type, groups) ->
                selectedGroups.put(type, new LinkedHashSet<>(groups)));
        priority = menu.priority();
        deliveryStrategy = menu.deliveryStrategy();
    }

    @Override protected void init()
    {
        imageWidth = 276;
        imageHeight = 260;
        super.init();
        menu.setFilterEditing(filterEditing);
        search = null;
        returnAll = null;
        deliveryStrategyButton = null;
        if (filterEditing) { dropdownOpen = false; initFilterEditor(); return; }
        addRenderableWidget(Button.builder(Component.translatable("gui.beyond_craftlines.filter.open"), ignored -> {
            setFilterEditing(true);
        }).bounds(leftPos + imageWidth - 76, topPos + 8, 64, 18).build());
        rows.clear();
        groupRows.clear();
        dropdownOpen = manualFallback;
        if (manualFallback)
        {
            reloadManualCandidates();
            search = addRenderableWidget(new EditBox(font, leftPos + 12, topPos + 43,
                    imageWidth - 24, 18, Component.translatable(
                    "gui.beyond_craftlines.provisioner.manual_search")));
            search.setMaxLength(128);
            search.setResponder(ignored -> {
                if (search.isFocused()) dropdownOpen = true;
                if (candidates.isEmpty()) reloadManualCandidates();
                dropdownScroll = 0;
                refresh();
            });
        }
        for (int row = 0; row < ROWS; row++)
        {
            final int index = row;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> toggle(index))
                    .bounds(leftPos + 12, rowY(row), imageWidth - 24, 18).build()));
            List<Button> groups = new ArrayList<>();
            for (int group = 0; group < MAX_GROUPS; group++)
            {
                final int groupIndex = group;
                groups.add(addRenderableWidget(Button.builder(Component.empty(), ignored ->
                                toggleGroup(index, groupIndex))
                        .bounds(leftPos + 18, topPos + 64 + row * 40, 32, 16).build()));
            }
            groupRows.add(groups);
        }
        addPriorityButton("-10", -10, 72, 34);
        addPriorityButton("-1", -1, 110, 30);
        addPriorityButton("+1", 1, 190, 30);
        addPriorityButton("+10", 10, 224, 40);
        previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            if (page > 0) { page--; refresh(); }
        }).bounds(leftPos + 12, topPos + 229, 28, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            if (page + 1 < pageStarts(visibleCandidates()).size()) { page++; refresh(); }
        }).bounds(leftPos + 44, topPos + 229, 28, 18).build());
        if (!menu.isBoundMachineConfiguration())
        {
            deliveryStrategyButton = addRenderableWidget(Button.builder(
                    deliveryStrategyTitle(), ignored -> cycleDeliveryStrategy())
                    .bounds(leftPos + imageWidth - 132, topPos + 185, 120, 18).build());
            returnAll = addRenderableWidget(Button.builder(
                    Component.translatable("gui.beyond_craftlines.provisioner.return_all"), ignored -> {
                        returnAll.active = false;
                        PacketDistributor.sendToServer(new ReturnProvisionerContentPayload(menu.position().asLong()));
                    }).bounds(leftPos + imageWidth - 132, topPos + 229, 120, 18).build());
        }
        refresh();
    }

    public boolean isFilterEditing() { return filterEditing; }

    public net.minecraft.client.renderer.Rect2i filterTargetArea(int slot)
    { return new net.minecraft.client.renderer.Rect2i(leftPos + 57 + slot % ResourceFilter.COLUMNS * 18,
            topPos + 52 + slot / ResourceFilter.COLUMNS * 18, 18, 18); }

    public void setFilterEntry(int slot, IStackKey<?> key)
    {
        if (!filterEditing || slot < 0 || slot >= ResourceFilter.SIZE) return;
        menu.setResourceFilter(menu.resourceFilter().withEntry(slot, key));
        sendResourceFilter();
    }

    public void addFilterEntry(IStackKey<?> key)
    {
        if (!filterEditing || key == null || key.isEmpty()) return;
        for (int slot = 0; slot < ResourceFilter.SIZE; slot++)
            if (menu.resourceFilter().entries().get(slot).isEmpty()) { setFilterEntry(slot, key); return; }
        // A full list needs an explicit slot replacement; inventory clicks never overwrite a marker.
    }

    private void sendResourceFilter()
    { PacketDistributor.sendToServer(new ConfigureResourceFilterPayload(menu.position(), menu.resourceFilter())); }

    private void setFilterEditing(boolean value)
    {
        filterEditing = value;
        menu.setFilterEditing(value);
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    value ? ProvisionerConfigMenu.OPEN_FILTER : ProvisionerConfigMenu.CLOSE_FILTER);
        rebuildWidgets();
    }

    private void initFilterEditor()
    {
        filterControls.clear();
        String[] labels = {"whitelist", "blacklist", "nbt_ignore", "nbt_match", "match_inputs", "match_outputs", "clear"};
        for (int option = 0; option < labels.length; option++)
        {
            final int action = option;
            int x = leftPos + 53 + (option / 2) * 50 + (option % 2) * 21;
            if (option == 6) x = leftPos + 203;
            Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                ResourceFilter filter = menu.resourceFilter();
                filter = switch (action) {
                    case 0 -> filter.withOptions(true, filter.matchNbt(), filter.matchOutputs());
                    case 1 -> filter.withOptions(false, filter.matchNbt(), filter.matchOutputs());
                    case 2 -> filter.withOptions(filter.whitelist(), false, filter.matchOutputs());
                    case 3 -> filter.withOptions(filter.whitelist(), true, filter.matchOutputs());
                    case 4 -> filter.withOptions(filter.whitelist(), filter.matchNbt(), false);
                    case 5 -> filter.withOptions(filter.whitelist(), filter.matchNbt(), true);
                    default -> filter.clear();
                };
                menu.setResourceFilter(filter);
                sendResourceFilter();
            }).bounds(x, topPos + 27, option == 6 ? 20 : 21, 20).build());
            button.setTooltip(Tooltip.create(Component.translatable("gui.beyond_craftlines.filter." + labels[option])));
            filterControls.add(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.beyond_craftlines.filter.back"), ignored ->
                setFilterEditing(false)).bounds(leftPos + imageWidth - 76, topPos + 8, 64, 18).build());
    }

    private void renderFilterControls(GuiGraphics graphics)
    {
        if (!filterEditing) return;
        ResourceFilter filter = menu.resourceFilter();
        boolean[] selected = {filter.whitelist(), !filter.whitelist(), !filter.matchNbt(), filter.matchNbt(),
                !filter.matchOutputs(), filter.matchOutputs(), false};
        for (int option = 0; option < filterControls.size(); option++)
            FilterControlIcons.draw(graphics, filterControls.get(option), option, selected[option]);
    }

    private int filterSlotAt(double mouseX, double mouseY)
    {
        for (int slot = 0; slot < ResourceFilter.SIZE; slot++)
        {
            var area = filterTargetArea(slot);
            if (mouseX >= area.getX() && mouseX < area.getX() + 18
                    && mouseY >= area.getY() && mouseY < area.getY() + 18) return slot;
        }
        return -1;
    }

    private void drawFilterEditor(GuiGraphics graphics, int mouseX, int mouseY)
    {
        for (int slot = 0; slot < ResourceFilter.SIZE; slot++)
        {
            var area = filterTargetArea(slot);
            drawFilterSlot(graphics, area.getX(), area.getY());
            IStackKey<?> key = menu.resourceFilter().entries().get(slot);
            if (!key.isEmpty() && !(key instanceof ItemStackKey))
                key.getRender().render(graphics, key, area.getX() + 1, area.getY() + 1);
        }
        for (int index = ResourceFilter.SIZE; index < menu.slots.size(); index++)
        {
            var slot = menu.slots.get(index);
            drawFilterSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        int hovered = filterSlotAt(mouseX, mouseY);
        if (hovered >= 0)
        {
            var area = filterTargetArea(hovered);
            graphics.fill(area.getX() + 1, area.getY() + 1, area.getX() + 17, area.getY() + 17, 0x55FFFFFF);
        }
    }

    private static void drawFilterSlot(GuiGraphics graphics, int x, int y)
    {
        graphics.fill(x, y, x + 18, y + 18, 0xFF555555);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    private Component deliveryStrategyTitle()
    { return Component.translatable(deliveryStrategy.translationKey()); }

    private List<ResourceLocation> loadCandidates()
    {
        return (manualFallback ? JeiCatalystIndex.recipeTypes(
                menu.manualLoadedFamilies(), menu.manualRecipeAliases(),
                menu.debugRecipeTypeMappings()) : menu.candidates()).stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    private void reloadManualCandidates()
    {
        if (manualFallback) candidates = loadCandidates();
    }

    private void cycleDeliveryStrategy()
    {
        deliveryStrategy = deliveryStrategy.next();
        deliveryStrategySyncGrace = 20;
        deliveryStrategyButton.setMessage(deliveryStrategyTitle());
        PacketDistributor.sendToServer(new ConfigureProvisionerDeliveryStrategyPayload(
                menu.position().asLong(), deliveryStrategy.id()));
    }

    private void addPriorityButton(String label, int delta, int x, int width)
    {
        addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
            long nextPriority = (long) priority + delta;
            priority = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, nextPriority));
            PacketDistributor.sendToServer(new ConfigureBindingPriorityPayload(
                    menu.position().asLong(), priority));
        }).bounds(leftPos + x, topPos + 207, width, 18).build());
    }

    private void toggle(int row)
    {
        ResourceLocation type = displayedCandidate(row);
        if (type == null) return;
        if (manualFallback) return;
        if (!selected.remove(type))
        {
            selected.add(type);
            selectedGroups.put(type, new LinkedHashSet<>());
        }
        else selectedGroups.remove(type);
        sendConfiguration();
        refresh();
    }

    private void toggleGroup(int row, int groupIndex)
    {
        if (manualFallback) return;
        ResourceLocation type = displayedCandidate(row);
        if (type == null) return;
        if (!selected.contains(type)) return;
        List<String> groups = groups(type);
        if (groupIndex >= groups.size()) return;
        Set<String> chosen = selectedGroups.computeIfAbsent(type, ignored -> new LinkedHashSet<>());
        String group = groups.get(groupIndex);
        if (!chosen.remove(group)) chosen.add(group);
        sendConfiguration();
        refresh();
    }

    private void sendConfiguration()
    { PacketDistributor.sendToServer(ConfigureProvisionerPayload.of(
            menu.position(), selected, selectedGroups,
            com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry.encode(
                    JeiCatalystIndex.inputGroupsFor(selected)), priority)); }

    private void refresh()
    {
        List<ResourceLocation> visible = visibleCandidates();
        List<Integer> pageStarts = pageStarts(visible);
        page = Math.max(0, Math.min(page, pageStarts.size() - 1));
        int start = pageStarts.get(page);
        int end = page + 1 < pageStarts.size() ? pageStarts.get(page + 1) : visible.size();
        int entryY = topPos + CONTENT_TOP;
        for (int row = 0; row < rows.size(); row++)
        {
            int index = start + row;
            Button button = rows.get(row);
            button.visible = !manualFallback && index < end;
            if (!button.visible)
            {
                groupRows.get(row).forEach(group -> group.visible = false);
                continue;
            }
            ResourceLocation type = visible.get(index);
            button.setY(entryY);
            Component title = JeiCatalystIndex.recipeTypeTitle(type).orElse(Component.literal(type.toString()));
            button.setMessage(Component.literal(selected.contains(type) ? "[✓] " : "[ ] ").append(title));
            List<String> groups = groups(type);
            Set<String> chosen = selectedGroups.getOrDefault(type, Set.of());
            int groupCount = groups.size();
            int availableWidth = imageWidth - 36;
            for (int groupIndex = 0; groupIndex < groupRows.get(row).size(); groupIndex++)
            {
                Button groupButton = groupRows.get(row).get(groupIndex);
                groupButton.visible = !manualFallback && selected.contains(type)
                        && groupCount > 1 && groupIndex < groupCount;
                if (!groupButton.visible) continue;
                String group = groups.get(groupIndex);
                int groupRow = groupIndex / MAX_GROUP_COLUMNS;
                int column = groupIndex % MAX_GROUP_COLUMNS;
                int columns = Math.min(MAX_GROUP_COLUMNS,
                        groupCount - groupRow * MAX_GROUP_COLUMNS);
                int groupWidth = (availableWidth - (columns - 1) * GROUP_GAP_X) / columns;
                groupButton.active = true;
                groupButton.setX(leftPos + 18 + column * (groupWidth + GROUP_GAP_X));
                groupButton.setY(entryY + 20 + groupRow * (GROUP_HEIGHT + GROUP_GAP_Y));
                groupButton.setWidth(groupWidth);
                Component groupLabel = groupTitle(group);
                groupButton.setMessage(Component.literal(chosen.contains(group) ? "[✓] " : "[ ] ")
                        .append(groupLabel));
                groupButton.setTooltip(Tooltip.create(groupLabel));
            }
            entryY += entryHeight(type);
        }
        previous.active = page > 0;
        next.active = page + 1 < pageStarts.size();
        previous.visible = !manualFallback;
        next.visible = !manualFallback;
        if (returnAll != null) returnAll.active = menu.hasResources();
    }

    private int rowY(int row)
    { return topPos + CONTENT_TOP + row * 40; }

    private ResourceLocation displayedCandidate(int row)
    {
        List<ResourceLocation> visible = visibleCandidates();
        List<Integer> starts = pageStarts(visible);
        if (page < 0 || page >= starts.size()) return null;
        int index = starts.get(page) + row;
        int end = page + 1 < starts.size() ? starts.get(page + 1) : visible.size();
        return index < end ? visible.get(index) : null;
    }

    private List<Integer> pageStarts(List<ResourceLocation> visible)
    {
        if (visible.isEmpty()) return List.of(0);
        List<Integer> starts = new ArrayList<>();
        int contentHeight = (menu.isBoundMachineConfiguration() ? 203 : 183) - CONTENT_TOP;
        for (int start = 0; start < visible.size(); )
        {
            starts.add(start);
            int usedHeight = 0;
            int count = 0;
            while (start + count < visible.size() && count < ROWS)
            {
                int height = entryHeight(visible.get(start + count));
                if (count > 0 && usedHeight + height > contentHeight) break;
                usedHeight += height;
                count++;
            }
            start += Math.max(1, count);
        }
        return starts;
    }

    private int entryHeight(ResourceLocation type)
    {
        int groupCount = selected.contains(type) ? groups(type).size() : 0;
        if (groupCount <= 1) return 18 + ENTRY_GAP;
        int groupRowCount = (groupCount + MAX_GROUP_COLUMNS - 1) / MAX_GROUP_COLUMNS;
        return 20 + groupRowCount * GROUP_HEIGHT
                + (groupRowCount - 1) * GROUP_GAP_Y + ENTRY_GAP;
    }

    private List<ResourceLocation> visibleCandidates()
    {
        if (!manualFallback || search == null || search.getValue().isBlank()) return candidates;
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        return candidates.stream().filter(type -> type.toString().toLowerCase(Locale.ROOT).contains(query)
                || JeiCatalystIndex.recipeTypeTitle(type).map(Component::getString).orElse("")
                .toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private List<ResourceLocation> manualOptions()
    { return visibleCandidates(); }

    private int maxDropdownScroll()
    { return Math.max(0, manualOptions().size() - MANUAL_ROWS); }

    private boolean isOverDropdown(double mouseX, double mouseY)
    {
        if (!manualFallback || !dropdownOpen) return false;
        int rows = Math.max(1, Math.min(MANUAL_ROWS, manualOptions().size()));
        int x = leftPos + 12;
        int y = topPos + 62;
        return mouseX >= x && mouseX < x + imageWidth - 24
                && mouseY >= y && mouseY < y + rows * MANUAL_ROW_HEIGHT + 2;
    }

    private int hoveredDropdownOption(double mouseX, double mouseY)
    {
        if (!isOverDropdown(mouseX, mouseY)) return -1;
        int index = dropdownScroll + ((int) mouseY - (topPos + 63)) / MANUAL_ROW_HEIGHT;
        return index >= manualOptions().size() ? -1 : index;
    }

    private void selectManualOption(int index)
    {
        List<ResourceLocation> options = manualOptions();
        if (index < 0 || index >= options.size()) return;
        ResourceLocation type = options.get(index);
        selected.clear();
        selected.add(type);
        selectedGroups.clear();
        selectedGroups.put(type, new LinkedHashSet<>());
        search.setFocused(false);
        search.setValue(JeiCatalystIndex.recipeTypeTitle(type).map(Component::getString)
                .orElse(type.toString()));
        dropdownOpen = false;
        sendConfiguration();
        refresh();
    }

    private void renderManualDropdown(GuiGraphics graphics, int mouseX, int mouseY)
    {
        if (filterEditing || !manualFallback || !dropdownOpen) return;
        List<ResourceLocation> options = manualOptions();
        dropdownScroll = Math.max(0, Math.min(dropdownScroll, maxDropdownScroll()));
        int rows = Math.max(1, Math.min(MANUAL_ROWS, options.size()));
        int x = leftPos + 12;
        int y = topPos + 62;
        int width = imageWidth - 24;
        graphics.fill(x, y, x + width, y + rows * MANUAL_ROW_HEIGHT + 2, 0xFF55D5DA);
        graphics.fill(x + 1, y + 1, x + width - 1, y + rows * MANUAL_ROW_HEIGHT + 1, 0xFF0E1D24);
        if (options.isEmpty())
        {
            graphics.drawString(font, Component.translatable(
                            "gui.beyond_craftlines.provisioner.manual_no_matches"),
                    x + 4, y + 3, 0xFF9CB0B8, false);
            return;
        }
        for (int row = 0; row < rows; row++)
        {
            int index = dropdownScroll + row;
            if (index >= options.size()) break;
            int rowY = y + 1 + row * MANUAL_ROW_HEIGHT;
            if (hoveredDropdownOption(mouseX, mouseY) == index)
                graphics.fill(x + 1, rowY, x + width - 1, rowY + MANUAL_ROW_HEIGHT, 0xFF3A6972);
            ResourceLocation type = options.get(index);
            String title = JeiCatalystIndex.recipeTypeTitle(type).map(Component::getString)
                    .orElse(type.toString());
            String text = title + "  ·  " + type;
            graphics.drawString(font, font.plainSubstrByWidth(text, width - 8),
                    x + 4, rowY + 2, 0xFFD8F3FF, false);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int option = hoveredDropdownOption(mouseX, mouseY);
        if (option >= 0) { selectManualOption(option); return true; }
        boolean clickedSearch = search != null && search.isMouseOver(mouseX, mouseY);
        if (clickedSearch)
        {
            if (candidates.isEmpty()) reloadManualCandidates();
            dropdownOpen = true;
        }
        else if (!isOverDropdown(mouseX, mouseY)) dropdownOpen = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        // Container screens normally close on the inventory key before EditBox receives
        // the following character event. While searching, that binding is text input.
        if (search != null && search.isFocused() && minecraft != null
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (isOverDropdown(mouseX, mouseY))
        {
            dropdownScroll = Math.max(0, Math.min(maxDropdownScroll(),
                    dropdownScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void containerTick()
    {
        super.containerTick();
        if (returnAll != null) returnAll.active = menu.hasResources();
        if (deliveryStrategyButton != null)
        {
            if (deliveryStrategySyncGrace > 0) deliveryStrategySyncGrace--;
            else if (menu.deliveryStrategy() != deliveryStrategy)
            {
                deliveryStrategy = menu.deliveryStrategy();
                deliveryStrategyButton.setMessage(deliveryStrategyTitle());
            }
        }
    }

    private List<String> groups(ResourceLocation type)
    { return availableGroups.getOrDefault(type, Set.of()).stream().sorted().limit(MAX_GROUPS).toList(); }

    private static Component groupTitle(String group)
    {
        String resourceType = com.amicbeam.beyondcraftlines.common.crafting.JeiSlotInputGroup
                .resourceType(group);
        if (!resourceType.isEmpty()) return Component.translatable(
                "gui.beyond_craftlines.provisioner.group." + resourceType);
        return switch (group)
        {
            case "ingredients" -> Component.translatable("gui.beyond_craftlines.provisioner.group.ingredients");
            case "catalyst" -> Component.translatable("gui.beyond_craftlines.provisioner.group.catalyst");
            case "container" -> Component.translatable("gui.beyond_craftlines.provisioner.group.container");
            case "tool" -> Component.translatable("gui.beyond_craftlines.provisioner.group.tool");
            case "activation_item" -> Component.translatable(
                    "gui.beyond_craftlines.provisioner.group.activation_item");
            case "main_offering" -> Component.translatable(
                    "gui.beyond_craftlines.provisioner.group.main_offering");
            case "offerings" -> Component.translatable("gui.beyond_craftlines.provisioner.group.offerings");
            case "reagent", "pedestal_items", "spirits", "extra_items", "input" -> Component.translatable(
                    "gui.beyond_craftlines.provisioner.group." + group);
            default -> Component.literal(group.replace('_', ' '));
        };
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        // Vanilla container-style light gray panel with the classic raised bevel.
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        if (filterEditing) { drawFilterEditor(graphics, mouseX, mouseY); return; }
        graphics.fill(leftPos + 144, topPos + 207, leftPos + 186, topPos + 225, 0xFF555555);
        graphics.fill(leftPos + 145, topPos + 208, leftPos + 186, topPos + 225, 0xFFFFFFFF);
        graphics.fill(leftPos + 145, topPos + 208, leftPos + 185, topPos + 224, 0xFF8B8B8B);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(font, title, 12, 10, 0x404040, false);
        if (filterEditing)
        {
            graphics.drawString(font, Component.translatable("container.inventory"), 57, 139, 0x404040, false);
            graphics.drawString(font, Component.translatable("gui.beyond_craftlines.filter.help"), 12, 127, 0x404040, false);
            return;
        }
        Component help = manualFallback
                ? Component.translatable("gui.beyond_craftlines.provisioner.manual_help")
                : candidates.isEmpty()
                ? Component.translatable("gui.beyond_craftlines.provisioner.no_candidates")
                : Component.translatable("gui.beyond_craftlines.provisioner.choose");
        graphics.drawString(font, help, 12, 27, candidates.isEmpty() && !manualFallback
                ? 0x777777 : 0x404040, false);
        if (!menu.isBoundMachineConfiguration())
            graphics.drawString(font, Component.translatable(
                    "gui.beyond_craftlines.provisioner.connections",
                    menu.supplyConnectionCount(), menu.extractConnectionCount()),
                    12, 190, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.beyond_craftlines.priority"), 12, 212,
                0x404040, false);
        graphics.drawCenteredString(font, Integer.toString(priority), 165, 212, 0xFFFFFF);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFilterControls(graphics);
        renderManualDropdown(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
        if (filterEditing)
        {
            int slot = filterSlotAt(mouseX, mouseY);
            if (slot >= 0 && !menu.resourceFilter().entries().get(slot).isEmpty())
            {
                var key = menu.resourceFilter().entries().get(slot);
                key.getRender().renderTooltip(graphics, font, key, 1, mouseX, mouseY);
            }
        }
    }
}
