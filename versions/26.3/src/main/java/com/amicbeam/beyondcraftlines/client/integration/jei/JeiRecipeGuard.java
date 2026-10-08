package com.amicbeam.beyondcraftlines.client.integration.jei;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Isolates optional Craftlines work for one displayed recipe, without disabling its category. */
final class JeiRecipeGuard
{
    private JeiRecipeGuard() {}

    static <T> T get(Supplier<T> action, T fallback, Consumer<Throwable> reportFailure)
    {
        try { return action.get(); }
        catch (RuntimeException | LinkageError failure)
        {
            // Diagnostics may also inspect unsupported third-party recipe accessors.
            try { reportFailure.accept(failure); }
            catch (RuntimeException | LinkageError ignored) {}
            return fallback;
        }
    }
}
