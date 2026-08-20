package com.amicbeam.beyondcraftlines.common.runtime;

/** Pure AE2-style blocking decision shared by native and directly bound machines. */
final class BlockingModeLogic
{
    private BlockingModeLogic() {}

    static boolean shouldWait(boolean enabled, boolean targetContainsRecipeInput)
    {
        return enabled && targetContainsRecipeInput;
    }

    static long craftsToDispatch(boolean enabled, long remainingCrafts)
    {
        if (remainingCrafts < 1) throw new IllegalArgumentException("remaining crafts must be positive");
        return enabled ? 1 : remainingCrafts;
    }

    static long amountToDispatch(boolean enabled, long remainingAmount, long remainingCrafts)
    {
        if (remainingAmount < 1 || remainingCrafts < 1)
            throw new IllegalArgumentException("remaining amount and crafts must be positive");
        return enabled ? ceilDiv(remainingAmount, remainingCrafts) : remainingAmount;
    }

    private static long ceilDiv(long value, long divisor)
    {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }
}
