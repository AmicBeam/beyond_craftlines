package com.amicbeam.beyondcraftlines.client;

import com.amicbeam.beyondcraftlines.client.integration.jei.JeiCatalystIndex;
import com.amicbeam.beyondcraftlines.common.menu.ProvisionerConfigMenu;
import com.amicbeam.beyondcraftlines.common.network.ConfigureProvisionerPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProvisionerConfigScreen extends AbstractContainerScreen<ProvisionerConfigMenu>
{
    private static final int ROWS = 8;
    private final List<Identifier> candidates;
    private final Set<Identifier> selected;
    private final List<Button> rows = new ArrayList<>();
    private int page;
    private Button previous;
    private Button next;

    public ProvisionerConfigScreen(ProvisionerConfigMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, 276, 238);
        candidates = menu.candidates().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
        selected = new LinkedHashSet<>(menu.selected());
    }

    @Override protected void init()
    {
        super.init();
        for (int row = 0; row < ROWS; row++)
        {
            final int index = row;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> toggle(index))
                    .bounds(leftPos + 12, topPos + 44 + row * 20, imageWidth - 24, 18).build()));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
            if (page > 0) { page--; refresh(); }
        }).bounds(leftPos + 12, topPos + 207, 28, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
            if ((page + 1) * ROWS < candidates.size()) { page++; refresh(); }
        }).bounds(leftPos + 44, topPos + 207, 28, 18).build());
        refresh();
    }

    private void toggle(int row)
    {
        int index = page * ROWS + row;
        if (index >= candidates.size()) return;
        Identifier type = candidates.get(index);
        if (!selected.remove(type)) selected.add(type);
        ClientPacketDistributor.sendToServer(ConfigureProvisionerPayload.of(menu.position(), selected));
        refresh();
    }

    private void refresh()
    {
        for (int row = 0; row < rows.size(); row++)
        {
            int index = page * ROWS + row;
            Button button = rows.get(row);
            button.visible = index < candidates.size();
            if (!button.visible) continue;
            Identifier type = candidates.get(index);
            Component title = JeiCatalystIndex.recipeTypeTitle(type).orElse(Component.literal(type.toString()));
            button.setMessage(Component.literal(selected.contains(type) ? "[✓] " : "[ ] ").append(title));
        }
        previous.active = page > 0;
        next.active = (page + 1) * ROWS < candidates.size();
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        // Vanilla container-style light gray panel with the classic raised bevel.
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, 0xFFFFFFFF);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        graphics.text(font, title, 12, 10, 0x404040, false);
        Component help = candidates.isEmpty()
                ? Component.translatable("gui.beyond_craftlines.provisioner.no_candidates")
                : Component.translatable("gui.beyond_craftlines.provisioner.choose");
        graphics.text(font, help, 12, 27, candidates.isEmpty() ? 0x777777 : 0x404040, false);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
