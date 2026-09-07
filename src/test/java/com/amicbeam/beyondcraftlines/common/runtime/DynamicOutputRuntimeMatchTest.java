package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicOutputRuntimeMatchTest
{
    @Test void acceptsTheActualKeyOnlyThroughItsConfiguredProducer()
    {
        Blade planned = new Blade("slashblade:slashblade", "jei-template");
        Blade actual = new Blade("slashblade:slashblade", "assembled-state");
        Producer slashBlade = new Producer("slashblade:white_sheath", planned);

        assertTrue(DynamicOutputRuntimeMatch.matches(planned, actual, List.of(slashBlade),
                Producer::output, Blade::exact, (producer, expected, candidate) ->
                        producer.recipe().startsWith("slashblade:") && expected.sameItem(candidate)));
        assertFalse(DynamicOutputRuntimeMatch.matches(planned, actual,
                List.of(new Producer("minecraft:stick", planned)), Producer::output, Blade::exact,
                (producer, expected, candidate) -> producer.recipe().startsWith("slashblade:")
                        && expected.sameItem(candidate)));
    }

    private record Producer(String recipe, Blade output) {}
    private record Blade(String item, String state)
    {
        private boolean exact(Blade other) { return item.equals(other.item) && state.equals(other.state); }
        private boolean sameItem(Blade other) { return item.equals(other.item); }
    }
}
