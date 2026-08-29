package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.function.BiPredicate;

/** Applies potentially asymmetric equivalence predicates in both directions. */
final class SymmetricMatch
{
    private SymmetricMatch() {}

    static <T> boolean exact(T left, T right, BiPredicate<T, T> compatible)
    { return either(left, right, Object::equals, compatible); }

    static <T> boolean either(T left, T right, BiPredicate<T, T> primary,
                              BiPredicate<T, T> compatible)
    {
        return left != null && right != null && (primary.test(left, right) || primary.test(right, left)
                || compatible.test(left, right) || compatible.test(right, left));
    }
}
