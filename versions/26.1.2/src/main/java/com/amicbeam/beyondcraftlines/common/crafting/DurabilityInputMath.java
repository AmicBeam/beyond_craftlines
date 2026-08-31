package com.amicbeam.beyondcraftlines.common.crafting;

final class DurabilityInputMath
{
    private DurabilityInputMath() {}
    static long requiredTools(long crafts,long amountPerCraft,long usesPerTool)
    {
        if(crafts<1||amountPerCraft<1||usesPerTool<1)throw new IllegalArgumentException("invalid durability requirement");
        long tools=crafts/usesPerTool+(crafts%usesPerTool==0?0:1);
        return SaturatingLongMath.multiply(tools,amountPerCraft);
    }
}
