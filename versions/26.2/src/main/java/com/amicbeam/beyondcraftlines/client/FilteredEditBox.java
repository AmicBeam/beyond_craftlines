package com.amicbeam.beyondcraftlines.client;

import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Restores EditBox value filtering removed from Minecraft 26.2. */
final class FilteredEditBox extends EditBox
{
    private Predicate<String> filter = value -> true;
    private int maxLength = 32;
    private int highlightPos;

    FilteredEditBox(Font font, int x, int y, int width, int height, Component narration)
    {
        super(font, x, y, width, height, narration);
    }

    void setFilter(Predicate<String> filter)
    {
        this.filter = filter == null ? value -> true : filter;
    }

    @Override
    public void setMaxLength(int maxLength)
    {
        this.maxLength = maxLength;
        super.setMaxLength(maxLength);
    }

    @Override
    public void setHighlightPos(int pos)
    {
        super.setHighlightPos(pos);
        highlightPos = Mth.clamp(pos, 0, getValue().length());
    }

    @Override
    public void setValue(String value)
    {
        if (allowed(value)) super.setValue(value);
    }

    @Override
    public void insertText(String input)
    {
        String next = AmountInputFilter.nextValueAfterInsert(
                getValue(), getCursorPosition(), highlightPos, input, maxLength);
        if (allowed(next)) super.insertText(input);
    }

    @Override
    public void deleteCharsToPos(int pos)
    {
        if (getValue().isEmpty()) return;
        if (highlightPos != getCursorPosition())
        {
            insertText("");
            return;
        }
        int start = Math.min(pos, getCursorPosition());
        int end = Math.max(pos, getCursorPosition());
        String next = new StringBuilder(getValue()).delete(start, end).toString();
        if (allowed(next)) super.deleteCharsToPos(pos);
    }

    private boolean allowed(String value)
    {
        return filter.test(value);
    }
}
