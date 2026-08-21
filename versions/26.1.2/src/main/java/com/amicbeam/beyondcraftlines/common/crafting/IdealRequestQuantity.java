package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure integer batch-alignment logic, kept independent of the Minecraft runtime. */
final class IdealRequestQuantity
{
    private IdealRequestQuantity() {}

    static long suggest(String target, long requested, List<Batch> batches)
    {
        Map<String, Batch> byOutput = new HashMap<>();
        for (Batch batch : batches) byOutput.putIfAbsent(batch.output(), batch);
        Batch root = byOutput.get(target);
        if (root == null) return 0;
        long craftMultiple = craftMultiple(root, byOutput, new HashSet<>());
        long alignedOutput = SaturatingLongMath.multiply(root.outputPerCraft(), craftMultiple);
        if (alignedOutput < 1 || alignedOutput == Long.MAX_VALUE) return 0;
        long suggestion = SaturatingLongMath.multiply(
                SaturatingLongMath.ceilDiv(requested, alignedOutput), alignedOutput);
        return suggestion > requested ? suggestion : 0;
    }

    private static long craftMultiple(Batch batch, Map<String, Batch> byOutput, Set<String> visiting)
    {
        if (!visiting.add(batch.output())) return 1;
        long result = 1;
        try
        {
            for (Map.Entry<String, Long> input : batch.inputs().entrySet())
            {
                Batch child = byOutput.get(input.getKey());
                if (child == null || input.getValue() < batch.crafts()
                        || input.getValue() % batch.crafts() != 0) continue;
                long perCraft = input.getValue() / batch.crafts();
                long childUnit = SaturatingLongMath.multiply(child.outputPerCraft(),
                        craftMultiple(child, byOutput, visiting));
                if (childUnit == Long.MAX_VALUE) return Long.MAX_VALUE;
                result = lcm(result, childUnit / gcd(perCraft, childUnit));
                if (result == Long.MAX_VALUE) return result;
            }
            return result;
        }
        finally { visiting.remove(batch.output()); }
    }

    private static long gcd(long left, long right)
    {
        while (right != 0) { long next = left % right; left = right; right = next; }
        return Math.max(1, left);
    }

    private static long lcm(long left, long right)
    { return SaturatingLongMath.multiply(left / gcd(left, right), right); }

    record Batch(String output, long outputPerCraft, long crafts, Map<String, Long> inputs)
    {
        Batch { inputs = Map.copyOf(inputs); }
    }
}
