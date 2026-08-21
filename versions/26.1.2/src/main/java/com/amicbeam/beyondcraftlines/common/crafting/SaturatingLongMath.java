package com.amicbeam.beyondcraftlines.common.crafting;

/** Non-negative long arithmetic for recipe quantities. Values above the storage model are capped. */
public final class SaturatingLongMath
{
    private SaturatingLongMath() {}

    public static long add(long left, long right)
    {
        requireNonNegative(left, right);
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public static long multiply(long left, long right)
    {
        requireNonNegative(left, right);
        return left != 0 && right > Long.MAX_VALUE / left ? Long.MAX_VALUE : left * right;
    }

    public static long ceilDiv(long value, long divisor)
    {
        if (value < 0 || divisor < 1) throw new IllegalArgumentException("invalid non-negative division");
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static void requireNonNegative(long left, long right)
    {
        if (left < 0 || right < 0) throw new IllegalArgumentException("quantities must be non-negative");
    }
}
