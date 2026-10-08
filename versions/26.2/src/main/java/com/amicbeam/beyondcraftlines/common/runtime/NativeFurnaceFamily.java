package com.amicbeam.beyondcraftlines.common.runtime;

/** Maps BD native furnace implementations without loading Minecraft classes in unit tests. */
final class NativeFurnaceFamily
{
    private NativeFurnaceFamily() {}

    static String forClass(Class<?> type)
    {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
        {
            String family = forClassName(current.getName());
            if (family != null) return family;
        }
        return null;
    }

    static String forClassName(String name)
    {
        return switch (name)
        {
            case "com.wintercogs.beyonddimensions.common.block.entity.NetFurnaceBlockEntity" -> "smelting";
            case "com.wintercogs.beyonddimensions.common.block.entity.NetBlastFurnaceBlockEntity" -> "blasting";
            case "com.wintercogs.beyonddimensions.common.block.entity.NetSmokerBlockEntity" -> "smoking";
            default -> null;
        };
    }
}
