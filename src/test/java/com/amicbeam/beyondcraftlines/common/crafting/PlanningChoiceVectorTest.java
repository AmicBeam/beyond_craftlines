package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlanningChoiceVectorTest
{
    @Test
    void incrementsTheLastUntriedChoiceAndDiscardsTheTail()
    {
        PlanningChoiceVector choices = new PlanningChoiceVector();
        choices.beginAttempt();
        assertEquals(0, choices.choose(2));
        assertEquals(0, choices.choose(3));
        assertTrue(choices.advance());
        choices.beginAttempt();
        assertEquals(0, choices.choose(2));
        assertEquals(1, choices.choose(3));
        assertTrue(choices.advance());
        choices.beginAttempt();
        assertEquals(0, choices.choose(2));
        assertEquals(2, choices.choose(3));
        assertTrue(choices.advance());
        choices.beginAttempt();
        assertEquals(1, choices.choose(2));
        assertEquals(0, choices.choose(3));
        assertTrue(choices.advance());
        choices.beginAttempt();
        assertEquals(1, choices.choose(2));
        assertEquals(1, choices.choose(3));
        assertTrue(choices.advance());
        choices.beginAttempt();
        assertEquals(1, choices.choose(2));
        assertEquals(2, choices.choose(3));
        assertFalse(choices.advance());
    }

    @Test
    void ignoresSingletonChoices()
    {
        PlanningChoiceVector choices = new PlanningChoiceVector();
        choices.beginAttempt();
        assertEquals(0, choices.choose(1));
        assertEquals(0, choices.choose(2));
        assertTrue(choices.advance());
        choices.beginAttempt();
        assertEquals(0, choices.choose(1));
        assertEquals(1, choices.choose(2));
        assertFalse(choices.advance());
    }
    @Test
    void skipsIndependentChoicesAfterTheFirstFailedDependency()
    {
        PlanningChoiceVector choices = new PlanningChoiceVector();
        choices.beginAttempt();
        assertEquals(0, choices.choose(2));
        choices.stopRecording();
        assertEquals(0, choices.choose(1000));
        assertTrue(choices.advance());
        choices.beginAttempt();
        assertEquals(1, choices.choose(2));
        assertFalse(choices.advance());
    }
}
