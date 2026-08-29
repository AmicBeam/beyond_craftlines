package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.network.ConfigureProvisionerPayload;
import com.amicbeam.beyondcraftlines.common.network.ConfigureBindingPriorityPayload;
import com.amicbeam.beyondcraftlines.common.network.ReturnProvisionerContentPayload;
import com.amicbeam.beyondcraftlines.common.network.ConfigureProvisionerDeliveryStrategyPayload;
import com.amicbeam.beyondcraftlines.common.runtime.ProvisionerDeliveryStrategy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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
    private List<Identifier> candidates;
    private final boolean manualFallback;
    private final Set<Identifier> selected;
    private final Map<Identifier, Set<String>> availableGroups;
    private final Map<Identifier, Set<String>> selectedGroups;
    private final List<Button> rows = new ArrayList<>();
    private final List<List<Button>> groupRows = new ArrayList<>();
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
        super(menu, inventory, title, 276, 260);
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
        super.init();
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
            if ((page + 1) * ROWS < visibleCandidates().size()) { page++; refresh(); }
        }).bounds(leftPos + 44, topPos + 229, 28, 18).build());
        if (!menu.isBoundMachineConfiguration())
        {
            deliveryStrategyButton = addRenderableWidget(Button.builder(
                    deliveryStrategyTitle(), ignored -> cycleDeliveryStrategy())
                    .bounds(leftPos + imageWidth - 132, topPos + 185, 120, 18).build());
            returnAll = addRenderableWidget(Button.builder(
                    Component.translatable("gui.beyond_craftlines.provisioner.return_all"), ignored -> {
                        returnAll.active = false;
                        ClientPacketDistributor.sendToServer(
                                new ReturnProvisionerContentPayload(menu.position().asLong()));
                    }).bounds(leftPos + imageWidth - 132, topPos + 229, 120, 18).build());
        }
        refresh();
    }

    private Component deliveryStrategyTitle()
    { return Component.translatable(deliveryStrategy.translationKey()); }

    private List<Identifier> loadCandidates()
    {
        return (manualFallback ? JeiCatalystIndex.recipeTypes(
                menu.manualLoadedFamilies(), menu.manualRecipeAliases(),
                menu.debugRecipeTypeMappings()) : menu.candidates()).stream()
                .sorted(Comparator.comparing(Identifier::toString)).toList();
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
        ClientPacketDistributor.sendToServer(new ConfigureProvisionerDeliveryStrategyPayload(
                menu.position().asLong(), deliveryStrategy.id()));
    }

    private void addPriorityButton(String label, int delta, int x, int width)
    {
        addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
            long nextPriority = (long) priority + delta;
            priority = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, nextPriority));
            ClientPacketDistributor.sendToServer(new ConfigureBindingPriorityPayload(
                    menu.position().asLong(), priority));
        }).bounds(leftPos + x, topPos + 207, width, 18).build());
    }

    private void toggle(int row)
    {
        List<Identifier> visible = visibleCandidates();
        int index = page * ROWS + row;
        if (index >= visible.size()) return;
        Identifier type = visible.get(index);
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
        int index = page * ROWS + row;
        if (index >= candidates.size()) return;
        Identifier type = candidates.get(index);
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
    { ClientPacketDistributor.sendToServer(ConfigureProvisionerPayload.of(
            menu.position(), selected, selectedGroups,
            com.amicbeam.beyondcraftlines.common.crafting.JeiInputGroupRegistry.encode(
                    JeiCatalystIndex.inputGroupsFor(selected)), priority)); }

    private void refresh()
    {
        List<Identifier> visible = visibleCandidates();
        for (int row = 0; row < rows.size(); row++)
        {
            int index = page * ROWS + row;
            Button button = rows.get(row);
            button.visible = !manualFallback && index < visible.size();
            if (!button.visible)
            {
                groupRows.get(row).forEach(group -> group.visible = false);
                continue;
            }
            Identifier type = visible.get(index);
            Component title = JeiCatalystIndex.recipeTypeTitle(type).orElse(Component.literal(type.toString()));
            button.setMessage(Component.literal(selected.contains(type) ? "[✓] " : "[ ] ").append(title));
            List<String> groups = groups(type);
            Set<String> chosen = selectedGroups.getOrDefault(type, Set.of());
            int groupCount = groups.size();
            int availableWidth = imageWidth - 36;
            int groupWidth = groupCount == 0 ? availableWidth
                    : Math.max(12, (availableWidth - Math.max(0, groupCount - 1) * 3) / groupCount);
            for (int groupIndex = 0; groupIndex < groupRows.get(row).size(); groupIndex++)
            {
                Button groupButton = groupRows.get(row).get(groupIndex);
                groupButton.visible = !manualFallback && selected.contains(type)
                        && groupCount > 1 && groupIndex < groupCount;
                if (!groupButton.visible) continue;
                String group = groups.get(groupIndex);
                groupButton.active = true;
                groupButton.setX(leftPos + 18 + groupIndex * (groupWidth + 3));
                groupButton.setY(topPos + 64 + row * 40);
                groupButton.setWidth(groupWidth);
                groupButton.setMessage(Component.literal(chosen.contains(group) ? "[✓] " : "[ ] ")
                        .append(groupTitle(group)));
            }
        }
        previous.active = page > 0;
        next.active = (page + 1) * ROWS < visible.size();
        previous.visible = !manualFallback;
        next.visible = !manualFallback;
        if (returnAll != null) returnAll.active = menu.hasResources();
    }

    private int rowY(int row)
    { return topPos + 44 + row * 40; }

    private List<Identifier> visibleCandidates()
    {
        if (!manualFallback || search == null || search.getValue().isBlank()) return candidates;
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        return candidates.stream().filter(type -> type.toString().toLowerCase(Locale.ROOT).contains(query)
                || JeiCatalystIndex.recipeTypeTitle(type).map(Component::getString).orElse("")
                .toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private List<Identifier> manualOptions()
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
        List<Identifier> options = manualOptions();
        if (index < 0 || index >= options.size()) return;
        Identifier type = options.get(index);
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

    private void extractManualDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (!manualFallback || !dropdownOpen) return;
        List<Identifier> options = manualOptions();
        dropdownScroll = Math.max(0, Math.min(dropdownScroll, maxDropdownScroll()));
        int rows = Math.max(1, Math.min(MANUAL_ROWS, options.size()));
        int x = leftPos + 12;
        int y = topPos + 62;
        int width = imageWidth - 24;
        graphics.fill(x, y, x + width, y + rows * MANUAL_ROW_HEIGHT + 2, 0xFF55D5DA);
        graphics.fill(x + 1, y + 1, x + width - 1, y + rows * MANUAL_ROW_HEIGHT + 1, 0xFF0E1D24);
        if (options.isEmpty())
        {
            graphics.text(font, Component.translatable(
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
            Identifier type = options.get(index);
            String title = JeiCatalystIndex.recipeTypeTitle(type).map(Component::getString)
                    .orElse(type.toString());
            String text = title + "  ·  " + type;
            graphics.text(font, font.plainSubstrByWidth(text, width - 8),
                    x + 4, rowY + 2, 0xFFD8F3FF, false);
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        double mouseX = event.x();
        double mouseY = event.y();
        int option = hoveredDropdownOption(mouseX, mouseY);
        if (option >= 0) { selectManualOption(option); return true; }
        boolean clickedSearch = search != null && search.isMouseOver(mouseX, mouseY);
        if (clickedSearch)
        {
            if (candidates.isEmpty()) reloadManualCandidates();
            dropdownOpen = true;
        }
        else if (!isOverDropdown(mouseX, mouseY)) dropdownOpen = false;
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean keyPressed(KeyEvent event)
    {
        // Container screens normally close on the inventory key before EditBox receives
        // the following character event. While searching, that binding is text input.
        if (search != null && search.isFocused() && minecraft != null
                && minecraft.options.keyInventory.matches(event)) return true;
        return super.keyPressed(event);
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

    private List<String> groups(Identifier type)
    { return availableGroups.getOrDefault(type, Set.of()).stream().sorted().limit(MAX_GROUPS).toList(); }

    private static Component groupTitle(String group)
    {
        return switch (group)
        {
            case "ingredients" -> Component.translatable("gui.beyond_craftlines.provisioner.group.ingredients");
            case "catalyst" -> Component.translatable("gui.beyond_craftlines.provisioner.group.catalyst");
            case "activation_item" -> Component.translatable(
                    "gui.beyond_craftlines.provisioner.group.activation_item");
            case "offerings" -> Component.translatable("gui.beyond_craftlines.provisioner.group.offerings");
            default -> Component.literal(group.replace('_', ' '));
        };
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        // Vanilla container-style light gray panel with the classic raised bevel.
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + 144, topPos + 207, leftPos + 186, topPos + 225, 0xFF555555);
        graphics.fill(leftPos + 145, topPos + 208, leftPos + 186, topPos + 225, 0xFFFFFFFF);
        graphics.fill(leftPos + 145, topPos + 208, leftPos + 185, topPos + 224, 0xFF8B8B8B);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        graphics.text(font, title, 12, 10, 0x404040, false);
        Component help = manualFallback
                ? Component.translatable("gui.beyond_craftlines.provisioner.manual_help")
                : candidates.isEmpty()
                ? Component.translatable("gui.beyond_craftlines.provisioner.no_candidates")
                : Component.translatable("gui.beyond_craftlines.provisioner.choose");
        graphics.text(font, help, 12, 27, candidates.isEmpty() && !manualFallback
                ? 0x777777 : 0x404040, false);
        if (!menu.isBoundMachineConfiguration())
            graphics.text(font, Component.translatable(
                    "gui.beyond_craftlines.provisioner.connections",
                    menu.supplyConnectionCount(), menu.extractConnectionCount()),
                    12, 190, 0x404040, false);
        graphics.text(font, Component.translatable("gui.beyond_craftlines.priority"), 12, 212,
                0x404040, false);
        graphics.centeredText(font, Integer.toString(priority), 165, 212, 0xFFFFFF);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractManualDropdown(graphics, mouseX, mouseY);
    }
}
