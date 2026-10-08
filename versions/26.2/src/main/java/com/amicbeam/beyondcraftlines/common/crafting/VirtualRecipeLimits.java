package com.amicbeam.beyondcraftlines.common.crafting;

/** Shared capture and wire limits. A recipe must fit in full; truncation changes its meaning. */
public final class VirtualRecipeLimits
{
    public static final int INPUTS = 256;
    public static final int CANDIDATES = 256;
    public static final int TOTAL_CANDIDATES = 8192;
    public static final int OUTPUTS = 64;

    private VirtualRecipeLimits() {}

    public static void requireInputs(int slots, long candidates)
    {
        if (slots < 1 || slots > INPUTS || candidates < slots || candidates > TOTAL_CANDIDATES)
            throw new IllegalArgumentException("virtual recipe exceeds input budget");
    }
}
