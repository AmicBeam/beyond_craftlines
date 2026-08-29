package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

/** Symmetric exact matching for keys reconstructed by different protocol and capability paths. */
public final class StackKeyMatch
{
    private StackKeyMatch() {}

    public static boolean exact(IStackKey<?> left, IStackKey<?> right)
    {
        return SymmetricMatch.either(left, right,
                (candidate, available) -> candidate.equals(available) || candidate.isSame(available),
                IStackKey::isSameTypeSameComponents);
    }
}
