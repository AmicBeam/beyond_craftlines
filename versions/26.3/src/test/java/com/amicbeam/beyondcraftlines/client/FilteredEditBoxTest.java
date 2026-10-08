package com.amicbeam.beyondcraftlines.client;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FilteredEditBoxTest
{
    @Test void deletionCannotTurnDashboardAmountIntoZero()
    {
        var box = box(AmountInputFilter::acceptsDashboardAmount);
        box.setValue("10");
        box.moveCursorToStart(false);
        var notifications = new ArrayList<String>();
        box.setResponder(notifications::add);

        box.deleteChars(1);

        assertEquals("10", box.getValue());
        assertEquals(0, box.getCursorPosition());
        assertTrue(notifications.isEmpty());
        box.moveCursorToEnd(false);
        box.deleteChars(-1);
        assertEquals("1", box.getValue());
        box.deleteChars(-1);
        assertEquals("", box.getValue());
    }

    @Test void pasteAndSelectionUseTheActualWidgetFilter()
    {
        var box = box(AmountInputFilter::acceptsOrderAmount);
        box.setValue("12");
        box.moveCursorToStart(false);
        box.setHighlightPos(2);
        box.insertText("9223372036854775808");
        assertEquals("12", box.getValue());
        box.insertText(Long.toString(Long.MAX_VALUE));
        assertEquals(Long.toString(Long.MAX_VALUE), box.getValue());
        box.insertText("0");
        assertEquals(Long.toString(Long.MAX_VALUE), box.getValue());
        box.moveCursorToStart(false);
        box.setHighlightPos(box.getValue().length());
        box.insertText("");
        assertEquals("", box.getValue());
    }

    @Test void leadingZeroesRetainThePreviousPositiveLongRules()
    {
        var box = box(AmountInputFilter::acceptsDashboardAmount);
        box.setValue("01");
        assertEquals("01", box.getValue());
        box.setValue("00");
        assertEquals("01", box.getValue());
    }

    private static FilteredEditBox box(Predicate<String> filter)
    {
        // Exercise the real widget without creating a graphics device or loading glyphs.
        Font font = new Font(null) {
            @Override public int width(String text) { return text.length(); }
            @Override public String plainSubstrByWidth(String text, int width) {
                return plainSubstrByWidth(text, width, false);
            }
            @Override public String plainSubstrByWidth(String text, int width, boolean reverse) {
                int length = Math.min(text.length(), Math.max(0, width));
                return reverse ? text.substring(text.length() - length) : text.substring(0, length);
            }
        };
        var box = new FilteredEditBox(font, 0, 0, 120, 18, Component.empty());
        box.setFilter(filter);
        return box;
    }
}
