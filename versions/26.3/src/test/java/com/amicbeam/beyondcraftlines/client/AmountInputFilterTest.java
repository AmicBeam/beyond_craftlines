package com.amicbeam.beyondcraftlines.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AmountInputFilterTest
{
    private static final int MAX_LENGTH = 32;

    @Test void acceptsEmptyAndInRangeDigits()
    {
        assertTrue(AmountInputFilter.acceptsOrderAmount(""));
        assertTrue(AmountInputFilter.acceptsDashboardAmount(""));
        assertTrue(AmountInputFilter.acceptsOrderAmount("1"));
        assertTrue(AmountInputFilter.acceptsDashboardAmount("1"));
        assertTrue(AmountInputFilter.acceptsOrderAmount("0"));
        assertFalse(AmountInputFilter.acceptsDashboardAmount("0"));
        assertTrue(AmountInputFilter.acceptsOrderAmount(Long.toString(Long.MAX_VALUE)));
        assertTrue(AmountInputFilter.acceptsDashboardAmount(Long.toString(Long.MAX_VALUE)));
    }

    @Test void rejectsInvalidDigitsAndOverflow()
    {
        assertFalse(AmountInputFilter.acceptsOrderAmount("12a"));
        assertFalse(AmountInputFilter.acceptsDashboardAmount("12a"));
        assertFalse(AmountInputFilter.acceptsOrderAmount("9223372036854775808"));
        assertFalse(AmountInputFilter.acceptsDashboardAmount("9223372036854775808"));
        assertFalse(AmountInputFilter.acceptsOrderAmount("12345678901234567890"));
        assertTrue(AmountInputFilter.acceptsDashboardAmount("01"));
        assertTrue(AmountInputFilter.acceptsOrderAmount("01"));
        assertFalse(AmountInputFilter.acceptsDashboardAmount("00"));
        assertTrue(AmountInputFilter.acceptsOrderAmount("00"));
    }

    @Test void pasteKeepsValidDigitsAndRejectsInvalidText()
    {
        assertEquals("123", paste("", "123", AmountInputFilter::acceptsOrderAmount));
        assertEquals("123", paste("", "123", AmountInputFilter::acceptsDashboardAmount));
        assertEquals("", paste("", "abc", AmountInputFilter::acceptsOrderAmount));
        assertEquals("12", paste("12", "abc", AmountInputFilter::acceptsOrderAmount));
        assertEquals("12", paste("12", "12a3", AmountInputFilter::acceptsOrderAmount));
        assertEquals(Long.toString(Long.MAX_VALUE),
                paste("", Long.toString(Long.MAX_VALUE), AmountInputFilter::acceptsOrderAmount));
        assertEquals("", paste("", "9223372036854775808", AmountInputFilter::acceptsOrderAmount));
        assertEquals("", paste("", "12345678901234567890", AmountInputFilter::acceptsOrderAmount));
        assertEquals("1", paste("1", "2345678901234567890", AmountInputFilter::acceptsOrderAmount));
    }

    @Test void pasteReplacesSelectionAndCanClearToEmpty()
    {
        assertEquals("99", AmountInputFilter.applyInsert("12", 0, 2, "99", MAX_LENGTH,
                AmountInputFilter::acceptsOrderAmount));
        assertEquals("", AmountInputFilter.applyInsert("12", 0, 2, "", MAX_LENGTH,
                AmountInputFilter::acceptsOrderAmount));
        assertEquals("12", AmountInputFilter.applyInsert("12", 0, 2, "0", MAX_LENGTH,
                AmountInputFilter::acceptsDashboardAmount));
        assertEquals("0", AmountInputFilter.applyInsert("12", 0, 2, "0", MAX_LENGTH,
                AmountInputFilter::acceptsOrderAmount));
        assertEquals("15", AmountInputFilter.applyInsert("12", 1, 2, "5", MAX_LENGTH,
                AmountInputFilter::acceptsOrderAmount));
    }

    private static String paste(String current, String pasted, java.util.function.Predicate<String> filter)
    {
        return AmountInputFilter.applyInsert(current, current.length(), current.length(), pasted, MAX_LENGTH, filter);
    }
}
