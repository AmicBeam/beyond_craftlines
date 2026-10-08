package com.amicbeam.beyondcraftlines.common.crafting;

/** Quantity rules for recipes that consume some of the same resource they produce. */
public final class SelfIncrementRecipe
{
    private SelfIncrementRecipe() {}

    public static Shape analyze(long outputPerCraft, long seedPerCraft, long consumedSeedPerCraft,
                                long requested)
    {
        if (outputPerCraft < 1 || seedPerCraft < 0 || consumedSeedPerCraft < 0 || requested < 1
                || consumedSeedPerCraft > seedPerCraft)
            throw new IllegalArgumentException("invalid self-increment recipe quantities");
        if (seedPerCraft == 0 || outputPerCraft <= consumedSeedPerCraft)
            return new Shape(false, 0, outputPerCraft,
                    SaturatingLongMath.ceilDiv(requested, outputPerCraft));
        long netOutputPerCraft = outputPerCraft - consumedSeedPerCraft;
        return new Shape(true, seedPerCraft, netOutputPerCraft,
                SaturatingLongMath.ceilDiv(requested, netOutputPerCraft));
    }

    public record Shape(boolean selfIncrement, long seed, long netOutputPerCraft, long crafts)
    {
        public Shape
        {
            if (seed < 0 || netOutputPerCraft < 1 || crafts < 1 || selfIncrement != (seed > 0))
                throw new IllegalArgumentException("invalid self-increment recipe shape");
        }
    }
}
