package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.HashSet;
import java.util.Set;

/** Bounded diagnostic state; repeated slow calls still contribute to the summary. */
final class SlowRecipeLogLimiter
{
    enum Decision { FAST, LOG, SUPPRESSED, LIMIT_REACHED }
    record Key(String stage, String recipe, String type, String recipeClass) {}
    record Summary(long calls, int logged, long suppressed, long maxNanos, Key worst) {}

    private final long thresholdNanos;
    private final int maxLogs;
    private final Set<Key> logged = new HashSet<>();
    private long calls;
    private long suppressed;
    private long maxNanos;
    private Key worst;
    private boolean limitReported;

    SlowRecipeLogLimiter(long thresholdNanos, int maxLogs)
    {
        if (thresholdNanos < 1 || maxLogs < 1) throw new IllegalArgumentException("invalid slow log limits");
        this.thresholdNanos = thresholdNanos;
        this.maxLogs = maxLogs;
    }

    synchronized Decision record(Key key, long elapsedNanos)
    {
        if (elapsedNanos < thresholdNanos) return Decision.FAST;
        calls++;
        if (elapsedNanos > maxNanos) { maxNanos = elapsedNanos; worst = key; }
        if (logged.contains(key)) { suppressed++; return Decision.SUPPRESSED; }
        if (logged.size() < maxLogs) { logged.add(key); return Decision.LOG; }
        suppressed++;
        if (!limitReported) { limitReported = true; return Decision.LIMIT_REACHED; }
        return Decision.SUPPRESSED;
    }

    synchronized Summary summary()
    { return new Summary(calls, logged.size(), suppressed, maxNanos, worst); }

    synchronized void reset()
    {
        logged.clear();
        calls = suppressed = maxNanos = 0L;
        worst = null;
        limitReported = false;
    }
}
