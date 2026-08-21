package com.amicbeam.beyondcraftlines.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

/** EMI-style compact input-to-output preview embedded in a node tooltip. */
public final class ClientRecipePreviewTooltip implements ClientTooltipComponent
{
    private static final int COLUMNS = 5;
    private static final int SLOT = 20;
    private static final int ARROW_WIDTH = 24;
    private static final int PADDING = 4;
    private final RecipePreviewTooltip data;

    public ClientRecipePreviewTooltip(RecipePreviewTooltip data)
    {
        this.data = data;
    }

    @Override
    public int getHeight()
    {
        return Math.max(SLOT, rows() * SLOT) + PADDING * 2;
    }

    @Override
    public int getWidth(Font font)
    {
        return columns() * SLOT + ARROW_WIDTH + SLOT + PADDING * 2;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics)
    {
        int width = getWidth(font);
        int height = getHeight();
        graphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);
        graphics.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF555555);
        int contentX = x + PADDING;
        int contentY = y + PADDING;
        int inputHeight = rows() * SLOT;
        for (int index = 0; index < data.inputs().size(); index++)
        {
            int slotX = contentX + index % COLUMNS * SLOT;
            int slotY = contentY + index / COLUMNS * SLOT;
            renderSlot(graphics, font, data.inputs().get(index), slotX, slotY, 0xFF526273);
        }

        int inputWidth = columns() * SLOT;
        int centerY = contentY + Math.max(SLOT, inputHeight) / 2;
        graphics.drawCenteredString(font, "→", contentX + inputWidth + ARROW_WIDTH / 2,
                centerY - font.lineHeight / 2, 0xFF0077AA);
        renderSlot(graphics, font, data.output(), contentX + inputWidth + ARROW_WIDTH,
                centerY - SLOT / 2, 0xFFE58F16);
    }

    private int columns()
    {
        return Math.max(1, Math.min(COLUMNS, data.inputs().size()));
    }

    private int rows()
    {
        return Math.max(1, (data.inputs().size() + COLUMNS - 1) / COLUMNS);
    }

    private static void renderSlot(GuiGraphics graphics, Font font, KeyAmount stack,
                                   int x, int y, int outline)
    {
        graphics.fill(x, y, x + 18, y + 18, 0xFF111923);
        graphics.renderOutline(x, y, 18, 18, outline);
        stack.key().getRender().render(graphics, stack.key(), x + 1, y + 1);
        if (stack.amount() > 1) stack.key().getRender().renderAmount(
                graphics, stack.amount(), x + 1, y + 1);
    }
}
