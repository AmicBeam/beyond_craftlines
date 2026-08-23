package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.network.ConfigureProvisionerPayload;
import com.amicbeam.beyondcraftlines.common.network.ReturnProvisionerContentPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public final class ProvisionerConfigScreen extends AbstractContainerScreen<ProvisionerConfigMenu>
{
    private static final int ROWS = 4;
    private static final int MAX_GROUPS = 16;
    private final List<ResourceLocation> candidates;
    private final Set<ResourceLocation> selected;
    private final Map<ResourceLocation, Set<String>> availableGroups;
    private final Map<ResourceLocation, Set<String>> selectedGroups;
    private final List<Button> rows = new ArrayList<>();
    private final List<List<Button>> groupRows = new ArrayList<>();
    private int page;
    private Button previous;
    private Button next;
    private Button returnAll;

    public ProvisionerConfigScreen(ProvisionerConfigMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        candidates = menu.candidates().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        selected = new LinkedHashSet<>(menu.selected());
        availableGroups = menu.availableGroups();
        selectedGroups = new HashMap<>();
        menu.selectedGroups().forEach((type, groups) ->
                selectedGroups.put(type, new LinkedHashSet<>(groups)));
    }

    @Override protected void init()
    {
        imageWidth = 276;
        imageHeight = 238;
        super.init();
        for (int row = 0; row < ROWS; row++)
        {
            final int index = row;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> toggle(index))
                    .bounds(leftPos + 12, topPos + 44 + row * 40, imageWidth - 24, 18).build()));
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
        previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            if (page > 0) { page--; refresh(); }
        }).bounds(leftPos + 12, topPos + 207, 28, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            if ((page + 1) * ROWS < candidates.size()) { page++; refresh(); }
        }).bounds(leftPos + 44, topPos + 207, 28, 18).build());
        if (!menu.isBoundMachineConfiguration())
            returnAll = addRenderableWidget(Button.builder(
                    Component.translatable("gui.beyond_craftlines.provisioner.return_all"), ignored -> {
                        returnAll.active = false;
                        PacketDistributor.sendToServer(new ReturnProvisionerContentPayload(menu.position().asLong()));
                    }).bounds(leftPos + imageWidth - 132, topPos + 207, 120, 18).build());
        refresh();
    }

    private void toggle(int row)
    {
        int index = page * ROWS + row;
        if (index >= candidates.size()) return;
        ResourceLocation type = candidates.get(index);
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
        int index = page * ROWS + row;
        if (index >= candidates.size()) return;
        ResourceLocation type = candidates.get(index);
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
    { PacketDistributor.sendToServer(ConfigureProvisionerPayload.of(menu.position(), selected, selectedGroups)); }

    private void refresh()
    {
        for (int row = 0; row < rows.size(); row++)
        {
            int index = page * ROWS + row;
            Button button = rows.get(row);
            button.visible = index < candidates.size();
            if (!button.visible)
            {
                groupRows.get(row).forEach(group -> group.visible = false);
                continue;
            }
            ResourceLocation type = candidates.get(index);
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
                groupButton.visible = selected.contains(type) && groupCount > 1 && groupIndex < groupCount;
                if (!groupButton.visible) continue;
                String group = groups.get(groupIndex);
                groupButton.setX(leftPos + 18 + groupIndex * (groupWidth + 3));
                groupButton.setY(topPos + 64 + row * 40);
                groupButton.setWidth(groupWidth);
                groupButton.setMessage(Component.literal(chosen.contains(group) ? "[✓] " : "[ ] ")
                        .append(groupTitle(group)));
            }
        }
        previous.active = page > 0;
        next.active = (page + 1) * ROWS < candidates.size();
        if (returnAll != null) returnAll.active = menu.hasResources();
    }

    @Override protected void containerTick()
    {
        super.containerTick();
        if (returnAll != null) returnAll.active = menu.hasResources();
    }

    private List<String> groups(ResourceLocation type)
    { return availableGroups.getOrDefault(type, Set.of()).stream().sorted().limit(MAX_GROUPS).toList(); }

    private static Component groupTitle(String group)
    {
        return switch (group)
        {
            case "ingredients" -> Component.translatable("gui.beyond_craftlines.provisioner.group.ingredients");
            case "activation_item" -> Component.translatable(
                    "gui.beyond_craftlines.provisioner.group.activation_item");
            case "offerings" -> Component.translatable("gui.beyond_craftlines.provisioner.group.offerings");
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
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(font, title, 12, 10, 0x404040, false);
        Component help = candidates.isEmpty()
                ? Component.translatable("gui.beyond_craftlines.provisioner.no_candidates")
                : Component.translatable("gui.beyond_craftlines.provisioner.choose");
        graphics.drawString(font, help, 12, 27, candidates.isEmpty() ? 0x777777 : 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
