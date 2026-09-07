package com.amicbeam.beyondcraftlines.common.runtime;

import com.amicbeam.beyondcraftlines.common.crafting.RecipeIoProfileRegistry;
import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.crafting.StackKeyMatch;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Propagates a dynamic producer's output policy without discarding the actual produced key. */
final class DynamicOutputRuntimeMatch
{
    private DynamicOutputRuntimeMatch() {}

    static boolean matches(IStackKey<?> expected, IStackKey<?> candidate,
                           List<RecipePlan.Step> producers)
    {
        return matches(expected, candidate, producers, RecipePlan.Step::outputKey,
                StackKeyMatch::exact, (producer, planned, actual) ->
                        RecipeIoProfileRegistry.outputMatches(
                                producer.recipe().toString(), planned, actual));
    }

    static <T, P> boolean matches(T expected, T candidate, List<P> producers,
                                  Function<P, T> output, BiPredicate<T, T> exact,
                                  ProducerMatcher<P, T> configured)
    {
        if (exact.test(expected, candidate)) return true;
        for (P producer : producers)
            if (exact.test(expected, output.apply(producer))
                    && configured.matches(producer, expected, candidate)) return true;
        return false;
    }

    @FunctionalInterface
    interface ProducerMatcher<P, T>
    { boolean matches(P producer, T expected, T candidate); }
}
