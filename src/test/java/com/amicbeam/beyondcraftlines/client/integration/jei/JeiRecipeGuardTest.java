package com.amicbeam.beyondcraftlines.client.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class JeiRecipeGuardTest
{
    @Test void brokenDisplayRecipeDoesNotRemoveOtherButtonsInTheSameCategory()
    {
        Object firstButton = new Object();
        Object lastButton = new Object();
        List<Throwable> failures = new ArrayList<>();
        assertSame(firstButton, JeiRecipeGuard.get(() -> firstButton, null, failures::add));
        var unsupportedType = new UnsupportedOperationException("display-only recipe getType");
        assertNull(JeiRecipeGuard.get(() -> { throw unsupportedType; }, null, failures::add));
        assertSame(lastButton, JeiRecipeGuard.get(() -> lastButton, null, failures::add));
        assertEquals(List.of(unsupportedType), failures);
    }

    @Test void indexingContinuesAfterRuntimeAndLinkageFailures()
    {
        List<Throwable> failures = new ArrayList<>();
        var missingApi = new NoSuchMethodError("third-party recipe API");
        assertEquals(List.of(), JeiRecipeGuard.get(() -> { throw missingApi; }, List.of(), failures::add));
        assertEquals(List.of("valid"), JeiRecipeGuard.get(() -> List.of("valid"), List.of(), failures::add));
        assertEquals(List.of(missingApi), failures);
    }

    @Test void diagnosticFailureCannotCrashTheRecipePage()
    {
        assertNull(JeiRecipeGuard.get(() -> { throw new UnsupportedOperationException(); }, null,
                failure -> { throw new IllegalStateException("recipe ID unavailable"); }));
    }

    @Test void supportedAndIntentionallyAbsentButtonsKeepTheirOriginalResults()
    {
        AtomicInteger reports = new AtomicInteger();
        Object button = new Object();
        assertSame(button, JeiRecipeGuard.get(() -> button, null, failure -> reports.incrementAndGet()));
        assertNull(JeiRecipeGuard.get(() -> null, button, failure -> reports.incrementAndGet()));
        assertEquals(0, reports.get());
    }

    @Test void fatalErrorsAreNotSwallowed()
    {
        var fatal = new OutOfMemoryError("fatal");
        assertSame(fatal, assertThrows(OutOfMemoryError.class,
                () -> JeiRecipeGuard.get(() -> { throw fatal; }, null, failure -> fail("unexpected report"))));
    }
}
