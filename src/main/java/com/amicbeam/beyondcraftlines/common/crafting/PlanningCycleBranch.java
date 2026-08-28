package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.function.Function;
import java.util.function.Supplier;

/** Keeps a cyclic dependency local to the recipe candidate that introduced it. */
final class PlanningCycleBranch
{
    private PlanningCycleBranch() {}

    static <S> S evaluate(S baseline, Supplier<S> candidate, Function<S, S> cycleFallback)
    {
        try { return candidate.get(); }
        catch (Cycle ignored) { return cycleFallback.apply(baseline); }
    }

    static final class Cycle extends RuntimeException
    {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }
}
