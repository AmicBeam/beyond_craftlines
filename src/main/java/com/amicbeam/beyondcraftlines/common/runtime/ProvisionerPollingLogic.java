package com.amicbeam.beyondcraftlines.common.runtime;

import java.util.ArrayList;
import java.util.List;

/** Pure round-robin cursor behavior shared by provisioner activation and delivery. */
final class ProvisionerPollingLogic
{
    private ProvisionerPollingLogic() {}

    static int cursorOnActivation(boolean resetFromFirstBinding, int currentCursor)
    {
        if (currentCursor < 0) throw new IllegalArgumentException("cursor must not be negative");
        return resetFromFirstBinding ? 0 : currentCursor;
    }

    static List<Integer> roundRobinOrder(int size, int cursor)
    {
        if (size < 0 || cursor < 0 || size > 0 && cursor >= size)
            throw new IllegalArgumentException("invalid round-robin size or cursor");
        if (size == 0) return List.of();
        ArrayList<Integer> order = new ArrayList<>(size);
        for (int offset = 0; offset < size; offset++) order.add((cursor + offset) % size);
        return order;
    }
}
