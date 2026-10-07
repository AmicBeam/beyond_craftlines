package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SlowRecipeLogLimiterTest
{
    private static SlowRecipeLogLimiter.Key key(String stage, String recipe)
    { return new SlowRecipeLogLimiter.Key(stage, recipe, "test:type", "test.Recipe"); }

    @Test void thresholdAndRepeatedCallsPreserveWorstTimingWithoutRepeatedDetails()
    {
        var limiter = new SlowRecipeLogLimiter(20L, 128);
        var key = key("layout", "test:recipe");
        assertEquals(SlowRecipeLogLimiter.Decision.FAST, limiter.record(key, 19L));
        assertEquals(0L, limiter.summary().calls());
        assertEquals(SlowRecipeLogLimiter.Decision.LOG, limiter.record(key, 20L));
        assertEquals(SlowRecipeLogLimiter.Decision.SUPPRESSED, limiter.record(key, 150L));
        assertEquals(new SlowRecipeLogLimiter.Summary(2, 1, 1, 150, key), limiter.summary());
        assertEquals(SlowRecipeLogLimiter.Decision.LOG, limiter.record(key("capture", "test:recipe"), 30L));
    }

    @Test void capsDistinctDetailsAndReportsTheLimitOnlyOnce()
    {
        var limiter = new SlowRecipeLogLimiter(20L, 2);
        assertEquals(SlowRecipeLogLimiter.Decision.LOG, limiter.record(key("layout", "test:a"), 21L));
        assertEquals(SlowRecipeLogLimiter.Decision.LOG, limiter.record(key("layout", "test:b"), 22L));
        var worst = key("capture", "test:c");
        assertEquals(SlowRecipeLogLimiter.Decision.LIMIT_REACHED, limiter.record(worst, 300L));
        for (int i = 0; i < 1000; i++)
            assertEquals(SlowRecipeLogLimiter.Decision.SUPPRESSED,
                    limiter.record(key("capture", "test:more_" + i), 25L));
        assertEquals(2, limiter.summary().logged());
        assertEquals(1003, limiter.summary().calls());
        assertEquals(1001, limiter.summary().suppressed());
        assertEquals(worst, limiter.summary().worst());
    }

    @Test void manualReloadOrRuntimeResetAllowsTheSameRecipeToBeReportedAgain()
    {
        var limiter = new SlowRecipeLogLimiter(20L, 1);
        var key = key("layout", "test:a");
        limiter.record(key, 21L);
        limiter.record(key("layout", "test:b"), 22L);
        limiter.reset();
        assertEquals(0, limiter.summary().calls());
        assertNull(limiter.summary().worst());
        assertEquals(SlowRecipeLogLimiter.Decision.LOG, limiter.record(key, 21L));
        assertEquals(SlowRecipeLogLimiter.Decision.LIMIT_REACHED, limiter.record(key("layout", "test:b"), 22L));
    }
}
