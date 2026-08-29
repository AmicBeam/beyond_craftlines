package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.Set;

/** Bridges vanilla JEI category UIDs to the execution families used by BD native furnaces. */
public final class NativeFurnaceRecipeFamilies
{
    private NativeFurnaceRecipeFamilies() {}

    public static String executionFamily(String jeiType)
    {
        return switch (jeiType)
        {
            case "minecraft:smelting" -> "smelting";
            case "minecraft:blasting" -> "blasting";
            case "minecraft:smoking" -> "smoking";
            default -> jeiType;
        };
    }

    public static boolean isAvailable(String jeiType, Set<String> availableFamilies)
    {
        String executionFamily = executionFamily(jeiType);
        return !executionFamily.equals(jeiType) && availableFamilies.contains(executionFamily);
    }
}
