package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.List;
import java.util.function.IntPredicate;

/** Pure dependency checks used by the parallel order scheduler. */
final class StepDependencyGraph
{
    private StepDependencyGraph() {}

    static boolean ready(int step, int size, List<Integer> dependencies, IntPredicate complete)
    {
        for (int dependency : dependencies)
            if (dependency < 0 || dependency >= step || dependency >= size || !complete.test(dependency))
                return false;
        return true;
    }
}
