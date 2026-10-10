package com.amicbeam.beyondcraftlines.common.crafting;

import java.util.ArrayList;
import java.util.List;

/**
 * Chronological choice vector for whole-attempt replay from initial stock.
 * Choice points are recorded only when a recipe or ingredient slot has multiple candidates.
 * After a failed attempt, {@link #advance()} increments the last untried choice and discards the tail.
 */
final class PlanningChoiceVector
{
    private final List<Integer> choices = new ArrayList<>();
    private final List<Integer> limits = new ArrayList<>();
    private int cursor;
    private boolean recording = true;

    void beginAttempt()
    {
        cursor = 0;
        recording = true;
    }

    int choose(int candidateCount)
    {
        if (candidateCount < 1) throw new IllegalArgumentException("no candidates");
        if (candidateCount == 1 || !recording) return 0;
        if (cursor < choices.size())
        {
            int index = choices.get(cursor);
            cursor++;
            return index < candidateCount ? index : candidateCount - 1;
        }
        choices.add(0);
        limits.add(candidateCount);
        cursor++;
        return 0;
    }

    /** Later independent choices cannot repair the first failed dependency. */
    void stopRecording()
    { recording = false; }

    boolean advance()
    {
        while (choices.size() > cursor)
        {
            choices.remove(choices.size() - 1);
            limits.remove(limits.size() - 1);
        }
        for (int i = choices.size() - 1; i >= 0; i--)
        {
            if (choices.get(i) + 1 < limits.get(i))
            {
                choices.set(i, choices.get(i) + 1);
                while (choices.size() > i + 1)
                {
                    choices.remove(choices.size() - 1);
                    limits.remove(limits.size() - 1);
                }
                return true;
            }
        }
        return false;
    }
}
