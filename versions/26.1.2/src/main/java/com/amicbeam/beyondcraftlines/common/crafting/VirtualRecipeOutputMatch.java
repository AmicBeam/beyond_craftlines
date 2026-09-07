package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Safely applies an output matcher only when a virtual resource exposes the expected stack type. */
final class VirtualRecipeOutputMatch
{
    private VirtualRecipeOutputMatch() {}

    static <T> boolean matches(Object output, Class<T> expectedType, Predicate<T> matcher)
    { return expectedType.isInstance(output) && matcher.test(expectedType.cast(output)); }

    static <T> boolean matches(boolean virtual, Object output, Class<T> expectedType, Predicate<T> matcher,
                               BooleanSupplier regularRecipeMatch)
    { return virtual ? matches(output, expectedType, matcher) : regularRecipeMatch.getAsBoolean(); }
}
