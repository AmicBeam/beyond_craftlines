package com.amicbeam.beyondcraftlines.common.runtime;

final class ExternalOrderLogic
{
    private ExternalOrderLogic() {}

    static long availableMachineOutput(long baseline, long current)
    {
        if (baseline < 0 || current < 0) return 0;
        return Math.max(0, current - baseline);
    }

    static long remainingInput(long requested, long extracted, long inserted)
    {
        if (requested < 0 || extracted < 0 || inserted < 0 || inserted > extracted) return requested;
        return requested - Math.min(requested, inserted);
    }

    static NetworkCredit creditNetworkOutput(long baseline, long current, long observed,
                                              long collected, long target)
    {
        if (baseline < 0 || current < 0 || observed < 0 || collected < 0 || target < 1)
            return new NetworkCredit(Math.max(0, observed), Math.max(0, collected));
        long nowObserved = availableMachineOutput(baseline, current);
        if (nowObserved <= observed) return new NetworkCredit(observed, collected);
        long credit = Math.min(nowObserved - observed, Math.max(0, target - collected));
        return new NetworkCredit(nowObserved, collected + credit);
    }

    record NetworkCredit(long observed, long collected) {}
}
