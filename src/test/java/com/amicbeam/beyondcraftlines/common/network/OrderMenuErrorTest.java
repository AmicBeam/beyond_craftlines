package com.amicbeam.beyondcraftlines.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class OrderMenuErrorTest
{
    @Test void preservesJeiCategoryFailureForTheOpenedTreePage()
    {
        String encoded = OrderMenuError.translated("error.beyond_craftlines.invalid_order_category",
                "example:machine", "example:runtime");
        var decoded = OrderMenuError.decode(encoded);
        assertNotNull(decoded);
        assertEquals("error.beyond_craftlines.invalid_order_category", decoded.translationKey());
        assertEquals(java.util.List.of("example:machine", "example:runtime"), decoded.arguments());
    }

    @Test void doesNotTreatLegacyPlannerErrorsAsTranslatedPayloads()
    { assertNull(OrderMenuError.decode("target is unavailable")); }

    @Test void rejectsUnboundedOrForeignTranslationKeys()
    {
        assertThrows(IllegalArgumentException.class,
                () -> OrderMenuError.translated("chat.type.text", "unsafe"));
    }
}
