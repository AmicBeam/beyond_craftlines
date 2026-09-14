package com.amicbeam.beyondcraftlines.common.runtime;

/** Capacity-limited batch shares in binding order, with fixed first-target remainder priority. */
final class ProvisionerEvenSplitLogic
{
    private ProvisionerEvenSplitLogic() {}

    static long[] allocate(long amount, long[] capacities)
    {
        if (amount < 0) throw new IllegalArgumentException("negative amount");
        long[] result = new long[capacities.length];
        for (long capacity : capacities)
            if (capacity < 0) throw new IllegalArgumentException("negative capacity");
        long remaining = amount;
        while (remaining > 0)
        {
            int active = 0;
            for (int i = 0; i < capacities.length; i++)
                if (capacities[i] > result[i]) active++;
            if (active == 0) break;
            long share = remaining / active;
            // Fill capped targets first, then recompute a fair share for the rest.
            boolean capped = false;
            for (int i = 0; i < capacities.length; i++)
            {
                long room = capacities[i] - result[i];
                if (room > 0 && room <= share)
                {
                    result[i] += room;
                    remaining -= room;
                    capped = true;
                }
            }
            if (capped) continue;
            long remainder = remaining % active;
            for (int i = 0; i < capacities.length; i++)
            {
                if (capacities[i] <= result[i]) continue;
                long requested = share + (remainder > 0 ? 1 : 0);
                if (remainder > 0) remainder--;
                long assigned = Math.min(requested, capacities[i] - result[i]);
                result[i] += assigned;
                remaining -= assigned;
            }
        }
        return result;
    }
}
