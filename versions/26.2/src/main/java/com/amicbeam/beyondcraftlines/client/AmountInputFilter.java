package com.amicbeam.beyondcraftlines.client;

import java.util.function.Predicate;
import net.minecraft.util.StringUtil;

/** 26.2 EditBox no longer has setFilter; keep the previous amount-box rules here. */
final class AmountInputFilter
{
    private AmountInputFilter() {}

    static boolean acceptsOrderAmount(String value)
    {
        return value.matches("[0-9]{0,19}") && (value.isEmpty() || parsesLong(value, 0));
    }

    static boolean acceptsDashboardAmount(String value)
    {
        return value.matches("[0-9]{0,19}") && (value.isEmpty() || parsesLong(value, 1));
    }

    static String applyInsert(String current, int cursor, int highlight, String input, int maxLength,
            Predicate<String> filter)
    {
        String next = nextValueAfterInsert(current, cursor, highlight, input, maxLength);
        return filter.test(next) ? next : current;
    }

    static String nextValueAfterInsert(String current, int cursor, int highlight, String input, int maxLength)
    {
        int start = Math.min(cursor, highlight);
        int end = Math.max(cursor, highlight);
        int maxInsertionLength = maxLength - current.length() - (start - end);
        if (maxInsertionLength <= 0) return current;
        String text = StringUtil.filterText(input);
        if (maxInsertionLength < text.length())
        {
            if (Character.isHighSurrogate(text.charAt(maxInsertionLength - 1))) maxInsertionLength--;
            text = text.substring(0, maxInsertionLength);
        }
        return new StringBuilder(current).replace(start, end, text).toString();
    }

    private static boolean parsesLong(String value, long minInclusive)
    {
        try { return Long.parseLong(value) >= minInclusive; }
        catch (NumberFormatException ignored) { return false; }
    }
}
