package com.amicbeam.beyondcraftlines.common.runtime;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class ProvisionerEvenSplitLogicTest
{
    @Test void everyResourceAndBatchStartsAtFirstBinding()
    {
        long[] capacities = {64, 64, 64};
        assertArrayEquals(new long[]{3, 3, 2}, ProvisionerEvenSplitLogic.allocate(8, capacities));
        assertArrayEquals(new long[]{3, 3, 2}, ProvisionerEvenSplitLogic.allocate(8, capacities));
        assertArrayEquals(new long[]{2, 1, 1}, ProvisionerEvenSplitLogic.allocate(4, capacities));
        assertArrayEquals(new long[]{2, 2, 2}, ProvisionerEvenSplitLogic.allocate(6, capacities));
        assertArrayEquals(new long[]{1, 0, 0}, ProvisionerEvenSplitLogic.allocate(1, capacities));
    }

    @Test void redistributesCapacityLimitedSharesAndSkipsUnavailableTargets()
    {
        assertArrayEquals(new long[]{1, 4, 3}, ProvisionerEvenSplitLogic.allocate(8, new long[]{1, 64, 64}));
        assertArrayEquals(new long[]{0, 4, 4}, ProvisionerEvenSplitLogic.allocate(8, new long[]{0, 64, 64}));
        assertArrayEquals(new long[]{1, 2, 3}, ProvisionerEvenSplitLogic.allocate(20, new long[]{1, 2, 3}));
        assertArrayEquals(new long[0], ProvisionerEvenSplitLogic.allocate(20, new long[0]));
        assertArrayEquals(new long[]{0, 0}, ProvisionerEvenSplitLogic.allocate(0, new long[]{10, 10}));
    }

    @Test void conservesResourcesWithoutOverflow()
    {
        long[] shares = ProvisionerEvenSplitLogic.allocate(Long.MAX_VALUE,
                new long[]{Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE});
        assertEquals(Long.MAX_VALUE, shares[0] + shares[1] + shares[2]);
        assertEquals(shares[1] + 1, shares[0]);
        assertEquals(shares[1], shares[2]);
        Random random = new Random(53);
        for (int sample = 0; sample < 1000; sample++)
        {
            long amount = random.nextInt(1000);
            long[] capacities = {random.nextInt(100), random.nextInt(100), random.nextInt(100)};
            long[] actual = ProvisionerEvenSplitLogic.allocate(amount, capacities);
            assertEquals(Math.min(amount, capacities[0] + capacities[1] + capacities[2]),
                    actual[0] + actual[1] + actual[2]);
            for (int i = 0; i < actual.length; i++)
            {
                assertTrue(actual[i] >= 0 && actual[i] <= capacities[i]);
                for (int j = 0; j < actual.length; j++)
                    if (actual[j] < capacities[j]) assertTrue(actual[i] <= actual[j] + 1);
            }
        }
    }

    @Test void preservesExistingSavedStrategyIds()
    {
        assertEquals(ProvisionerDeliveryStrategy.ROUND_ROBIN, ProvisionerDeliveryStrategy.fromId(0));
        assertEquals(ProvisionerDeliveryStrategy.NEAREST_FIRST, ProvisionerDeliveryStrategy.fromId(1));
        assertEquals(ProvisionerDeliveryStrategy.FARTHEST_FIRST, ProvisionerDeliveryStrategy.fromId(2));
        assertEquals(ProvisionerDeliveryStrategy.EVEN_SPLIT, ProvisionerDeliveryStrategy.fromId(3));
        assertEquals(ProvisionerDeliveryStrategy.EVEN_SPLIT, ProvisionerDeliveryStrategy.FARTHEST_FIRST.next());
        assertEquals(ProvisionerDeliveryStrategy.ROUND_ROBIN, ProvisionerDeliveryStrategy.EVEN_SPLIT.next());
    }
}
