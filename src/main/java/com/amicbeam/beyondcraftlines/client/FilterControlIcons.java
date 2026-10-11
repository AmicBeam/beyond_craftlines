package com.amicbeam.beyondcraftlines.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;

/** Pixel glyphs adapted from Sky Logistics FilterListScreen (GPL-3.0). */
public final class FilterControlIcons
{
    private FilterControlIcons() {}

    public static void draw(GuiGraphics graphics, AbstractButton button, int option, boolean selected)
    {
        if (selected)
        {
            int color = option == 0 ? 0xFF3F8F3F : option == 1 ? 0xFFB84343 : 0xFF2F7F8C;
            int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
            graphics.fill(x, y, x + w, y + 1, color);
            graphics.fill(x, y + h - 1, x + w, y + h, color);
            graphics.fill(x, y, x + 1, y + h, color);
            graphics.fill(x + w - 1, y, x + w, y + h, color);
        }
        if (option < 2) drawWhitelistIcon(graphics, button, option == 0, selected);
        else if (option < 4) drawNbtIcon(graphics, button, option == 3, selected);
        else if (option < 6) drawDurabilityIcon(graphics, button, option == 5, selected);
        else drawClearIcon(graphics, button);
    }

    private static void drawWhitelistIcon(GuiGraphics graphics, AbstractButton button, boolean allow, boolean selected) {
        if (button == null) {
            return;
        }
        int color = button.active ? 0xFFE0E0E0 : 0xFFA0A0A0;
        int x = button.getX();
        int y = button.getY();
        int listColor = color;
        graphics.fill(x + 5, y + 5, x + 13, y + 6, listColor);
        graphics.fill(x + 5, y + 9, x + 12, y + 10, listColor);
        graphics.fill(x + 5, y + 13, x + 11, y + 14, listColor);
        if (allow) {
            drawCheck(graphics, x + 10, y + 8, color);
        } else {
            drawCross(graphics, x + 11, y + 8, color);
        }
    }

    private static void drawNbtIcon(GuiGraphics graphics, AbstractButton button, boolean enabled, boolean selected) {
        if (button == null) {
            return;
        }
        int color = button.active ? 0xFFE0E0E0 : 0xFFA0A0A0;
        int x = button.getX();
        int y = button.getY();
        graphics.fill(x + 6, y + 4, x + 14, y + 5, color);
        graphics.fill(x + 6, y + 4, x + 7, y + 16, color);
        graphics.fill(x + 13, y + 4, x + 14, y + 16, color);
        graphics.fill(x + 6, y + 15, x + 14, y + 16, color);
        graphics.fill(x + 8, y + 8, x + 12, y + 9, color);
        graphics.fill(x + 8, y + 11, x + 12, y + 12, color);
        if (!enabled) {
            drawSlash(graphics, x, y, color);
        }
    }

    private static void drawDurabilityIcon(GuiGraphics graphics, AbstractButton button, boolean enabled, boolean selected) {
        if (button == null) {
            return;
        }
        int color = button.active ? 0xFFE0E0E0 : 0xFFA0A0A0;
        int x = button.getX();
        int y = button.getY();
        graphics.fill(x + 7, y + 4, x + 13, y + 5, color);
        graphics.fill(x + 6, y + 5, x + 7, y + 16, color);
        graphics.fill(x + 13, y + 5, x + 14, y + 16, color);
        graphics.fill(x + 6, y + 15, x + 14, y + 16, color);
        graphics.fill(x + 8, y + 10, x + 12, y + 14, color);
        if (!enabled) {
            drawSlash(graphics, x, y, color);
        }
    }

    private static void drawClearIcon(GuiGraphics graphics, AbstractButton button) {
        if (button == null) {
            return;
        }
        int x = button.getX();
        int y = button.getY();
        int color = button.active ? 0xFFE0E0E0 : 0xFFA0A0A0;
        drawSlash(graphics, x, y, color);
        for (int offset = 0; offset < 8; offset++) {
            graphics.fill(x + 6 + offset, y + 5 + offset, x + 8 + offset, y + 7 + offset, color);
        }
    }

    private static void drawCheck(GuiGraphics graphics, int x, int y, int color) {
        for (int offset = 0; offset < 3; offset++) {
            graphics.fill(x + offset, y + 4 + offset, x + offset + 2, y + 6 + offset, color);
        }
        for (int offset = 0; offset < 5; offset++) {
            graphics.fill(x + 3 + offset, y + 6 - offset, x + 5 + offset, y + 8 - offset, color);
        }
    }

    private static void drawCross(GuiGraphics graphics, int x, int y, int color) {
        for (int offset = 0; offset < 6; offset++) {
            graphics.fill(x + offset, y + offset, x + offset + 2, y + offset + 2, color);
            graphics.fill(x + 5 - offset, y + offset, x + 7 - offset, y + offset + 2, color);
        }
    }

    private static void drawSlash(GuiGraphics graphics, int x, int y, int color) {
        for (int offset = 0; offset < 8; offset++) {
            graphics.fill(x + 6 + offset, y + 13 - offset, x + 8 + offset, y + 15 - offset, color);
        }
    }

}
