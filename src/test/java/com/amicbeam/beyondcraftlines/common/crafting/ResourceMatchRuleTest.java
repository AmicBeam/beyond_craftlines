package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ResourceMatchRuleTest
{
    @Test
    void roundTripsIgnoredPaths()
    {
        var rule = new ResourceMatchRule(ResourceMatchRule.Mode.IGNORE_PATHS,
                Set.of("/components/mod~1state/uuid", "/tag/bladeState/lastActionTime"));
        assertEquals(rule, ResourceMatchRule.decode(rule.encode()));
    }

    @Test
    void nonPathModesDiscardPaths()
    {
        assertEquals(ResourceMatchRule.STRICT, new ResourceMatchRule(
                ResourceMatchRule.Mode.STRICT, Set.of("/ignored")));
        assertEquals(ResourceMatchRule.ITEM_ONLY, ResourceMatchRule.decode("item"));
    }
}
