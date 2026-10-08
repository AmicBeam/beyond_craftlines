package com.amicbeam.beyondcraftlines.common.data;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Explicit menus own their scope; direct recipe-viewer orders reuse the last accessible network. */
public final class NetworkSelectionPolicy
{
    private NetworkSelectionPolicy() {}

    public static <T> T select(boolean hasMenuContext, Supplier<T> menu,
                               Supplier<T> remembered, Supplier<T> primary, Predicate<T> accessible)
    {
        if (hasMenuContext) return checked(menu.get(), accessible);
        T previous = checked(remembered.get(), accessible);
        return previous != null ? previous : checked(primary.get(), accessible);
    }

    private static <T> T checked(T network, Predicate<T> accessible)
    { return network != null && accessible.test(network) ? network : null; }
}
