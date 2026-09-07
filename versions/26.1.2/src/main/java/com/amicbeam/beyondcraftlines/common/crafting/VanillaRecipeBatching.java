package com.amicbeam.beyondcraftlines.common.crafting;

/** Fixed batch semantics for vanilla JEI categories whose displayed output is per slot. */
public final class VanillaRecipeBatching
{
    private static final String BREWING = "minecraft:brewing";
    private static final long BREWING_BATCH = 3;

    private VanillaRecipeBatching() {}

    public static long outputAmount(Object family, long displayedAmount)
    {
        if (displayedAmount < 1) throw new IllegalArgumentException("invalid displayed output amount");
        return family != null && BREWING.equals(family.toString())
                ? SaturatingLongMath.multiply(displayedAmount, BREWING_BATCH) : displayedAmount;
    }

    public static boolean validUploadedOutputAmount(Object family, long outputAmount)
    { return family == null || !BREWING.equals(family.toString()) || outputAmount == BREWING_BATCH; }
}
