package com.amicbeam.beyondcraftlines.common.crafting;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import java.util.List;
import java.util.function.BiPredicate;

/** Output gate for an already identified and executable JEI root recipe. */
public final class ExplicitRootOutputMatch
{
    private ExplicitRootOutputMatch() {}

    public static boolean matches(IStackKey<?> target, List<KeyAmount> declaredOutputs)
    {
        return matches(target, declaredOutputs.stream().map(KeyAmount::key).toList(),
                StackKeyMatch::exact, IStackKey::isSame);
    }

    /**
     * Prefer complete identity, then allow the declared output family for recipes whose concrete
     * components are produced only by {@code assemble()}. Callers must validate recipe identity and
     * execution family before using this fallback; ordinary recipe candidate lookup remains exact.
     */
    static <T> boolean matches(T target, Iterable<T> declaredOutputs,
                               BiPredicate<T, T> exact, BiPredicate<T, T> sameFamily)
    {
        for (T output : declaredOutputs)
            if (exact.test(target, output)) return true;
        for (T output : declaredOutputs)
            if (sameFamily.test(target, output)) return true;
        return false;
    }
}
