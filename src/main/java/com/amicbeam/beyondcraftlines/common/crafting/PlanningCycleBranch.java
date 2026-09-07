package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.function.Function;
import java.util.function.Supplier;

/** Keeps a cyclic dependency local to the recipe candidate that introduced it. */
final class PlanningCycleBranch
{
    private PlanningCycleBranch() {}

    static <S> S evaluate(S baseline, Supplier<S> candidate, Function<S, S> cycleFallback)
    { return evaluateWithStatus(baseline, candidate, cycleFallback).state(); }

    static <S> Evaluation<S> evaluateWithStatus(S baseline, Supplier<S> candidate,
                                                Function<S, S> cycleFallback)
    {
        try { return new Evaluation<>(candidate.get(), false); }
        catch (Cycle ignored) { return new Evaluation<>(cycleFallback.apply(baseline), true); }
    }

    record Evaluation<S>(S state, boolean cyclic) {}

    static final class Cycle extends RuntimeException
    {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }
}
