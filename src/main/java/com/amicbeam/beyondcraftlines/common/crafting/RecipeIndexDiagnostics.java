package com.amicbeam.beyondcraftlines.common.crafting;

/** Timings of individual synchronous calls, not time spent waiting between render frames. */
public final class RecipeIndexDiagnostics
{
    public static final long SLOW_NANOS = 20_000_000L;
    private static final int MAX_LOGS = 128;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("beyond_craftlines");
    private static final SlowRecipeLogLimiter LIMITER = new SlowRecipeLogLimiter(SLOW_NANOS, MAX_LOGS);
    private static long reportedCalls;

    private RecipeIndexDiagnostics() {}

    public static void record(String stage, long elapsedNanos, Object recipeId, Object type,
                              Class<?> recipeClass, int slots, long candidates)
    {
        if (elapsedNanos < SLOW_NANOS) return;
        var key = new SlowRecipeLogLimiter.Key(stage, String.valueOf(recipeId), String.valueOf(type),
                recipeClass.getName());
        var decision = LIMITER.record(key, elapsedNanos);
        if (decision == SlowRecipeLogLimiter.Decision.LOG)
            LOGGER.warn("[NBT-ORDER] slow recipe index stage={} recipe={} type={} recipeClass={} elapsedMs={} slots={} candidates={} thread={}",
                    key.stage(), key.recipe(), key.type(), key.recipeClass(), elapsedNanos / 1_000_000.0,
                    slots, candidates, Thread.currentThread().getName());
        else if (decision == SlowRecipeLogLimiter.Decision.LIMIT_REACHED)
            LOGGER.warn("[NBT-ORDER] slow recipe index detail limit reached maxLogs={}; further details suppressed until JEI runtime rebuild or /craftlines reload", MAX_LOGS);
    }

    public static synchronized void summarize(String reason)
    {
        var summary = LIMITER.summary();
        if (summary.calls() == reportedCalls) return;
        reportedCalls = summary.calls();
        var worst = summary.worst();
        LOGGER.warn("[NBT-ORDER] slow recipe index summary reason={} calls={} logged={} suppressed={} maxMs={} worstStage={} worstRecipe={} worstType={} worstClass={}",
                reason, summary.calls(), summary.logged(), summary.suppressed(), summary.maxNanos() / 1_000_000.0,
                worst.stage(), worst.recipe(), worst.type(), worst.recipeClass());
    }

    public static synchronized void reset()
    {
        summarize("runtime_reset");
        LIMITER.reset();
        reportedCalls = 0L;
    }
}
