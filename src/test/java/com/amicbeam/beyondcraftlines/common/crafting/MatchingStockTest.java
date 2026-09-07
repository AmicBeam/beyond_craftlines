package com.amicbeam.beyondcraftlines.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingStockTest
{
    @Test
    void consumesOnlyComponentVariantsAcceptedByIngredient()
    {
        LinkedHashMap<String, Long> supplied = new LinkedHashMap<>();
        supplied.put("potion:red", 3L);
        supplied.put("potion:blue", 5L);
        MatchingStock<String, String> stock = new MatchingStock<>(key -> key.substring(0, key.indexOf(':')),
                supplied);

        assertEquals(3, stock.consume("potion", key -> key.endsWith(":red"), 4));
        assertEquals(0, stock.available("potion", key -> key.endsWith(":red")));
        assertEquals(5, stock.available("potion", key -> key.endsWith(":blue")));
    }

    @Test
    void branchCopiesDoNotMutateTheOriginal()
    {
        MatchingStock<String, String> original = new MatchingStock<>(key -> key, java.util.Map.of("iron", 8L));
        MatchingStock<String, String> branch = original.copy();

        assertEquals(5, branch.consume("iron", ignored -> true, 5));
        assertEquals(3, branch.available("iron", ignored -> true));
        assertEquals(8, original.available("iron", ignored -> true));
    }

    @Test
    void producedSurplusIsConsumedBeforeInitialStockIsReserved()
    {
        MatchingStock<String, String> stock = new MatchingStock<>(key -> key,
                java.util.Map.of("plate", 8L));
        stock.add("plate", 3);
        AtomicLong reserved = new AtomicLong();

        assertEquals(5, stock.consume("plate", ignored -> true, 5,
                (key, amount) -> reserved.addAndGet(amount)));
        assertEquals(2, reserved.get());
        assertEquals(6, stock.available("plate", ignored -> true));
    }

    @Test
    void durabilityCapacityUsesDamagedToolsBeforeRequestingANewOne()
    {
        LinkedHashMap<String, Long> supplied = new LinkedHashMap<>();
        supplied.put("knife:60", 1L);
        supplied.put("knife:5", 1L);
        MatchingStock<String, String> stock = new MatchingStock<>(key -> "knife", supplied);

        assertEquals(2, stock.itemsForCapacity("knife", ignored -> true, 65,
                key -> Long.parseLong(key.substring(key.indexOf(':') + 1)), 64));
        assertEquals(3, stock.itemsForCapacity("knife", ignored -> true, 66,
                key -> Long.parseLong(key.substring(key.indexOf(':') + 1)), 64));
    }
}
