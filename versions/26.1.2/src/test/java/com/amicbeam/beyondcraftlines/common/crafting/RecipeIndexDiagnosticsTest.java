package com.amicbeam.beyondcraftlines.common.crafting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

final class RecipeIndexDiagnosticsTest
{
    @Test void emittedWarningsContainRecipeIdentityAndInclusiveTimingsWithoutDuplicateDetails()
    {
        RecipeIndexDiagnostics.reset();
        var messages = new ArrayList<String>();
        var appender = new AbstractAppender("recipe-index-test", null,
                PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY)
        {
            @Override public void append(LogEvent event) { messages.add(event.getMessage().getFormattedMessage()); }
        };
        Logger logger = (Logger) LogManager.getLogger("beyond_craftlines");
        Level previous = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try
        {
            Object fastIdentity = new Object()
            {
                @Override public String toString() { throw new AssertionError("fast identity was formatted"); }
            };
            assertDoesNotThrow(() -> RecipeIndexDiagnostics.record("fast", RecipeIndexDiagnostics.SLOW_NANOS - 1,
                    fastIdentity, fastIdentity, null, -1, -1));
            assertTrue(messages.isEmpty());

            RecipeIndexDiagnostics.record("jei_layout", 25_000_000L, "test:slow", "test:machine",
                    RecipeIndexDiagnosticsTest.class, 2, 32L);
            RecipeIndexDiagnostics.record("jei_layout", 60_000_000L, "test:slow", "test:machine",
                    RecipeIndexDiagnosticsTest.class, 2, 32L);
            RecipeIndexDiagnostics.summarize("test_complete");
            RecipeIndexDiagnostics.summarize("no_changes");

            assertEquals(2, messages.size());
            String detail = messages.get(0);
            for (String field : new String[]{"slow recipe index", "stage=jei_layout", "recipe=test:slow",
                    "type=test:machine", "recipeClass=" + getClass().getName(), "elapsedMs=25.0",
                    "slots=2", "candidates=32", "thread="}) assertTrue(detail.contains(field), detail);
            String summary = messages.get(1);
            for (String field : new String[]{"summary", "reason=test_complete", "calls=2", "logged=1",
                    "suppressed=1", "maxMs=60.0", "worstRecipe=test:slow"}) assertTrue(summary.contains(field), summary);
        }
        finally
        {
            logger.removeAppender(appender);
            logger.setLevel(previous);
            appender.stop();
            RecipeIndexDiagnostics.reset();
        }
    }
}
