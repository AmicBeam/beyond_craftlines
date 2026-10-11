package com.amicbeam.beyondcraftlines.common.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Keeps every active order accessible while bounding each response and terminal history. */
final class OrderStatusPagination
{
    static final int PAGE_SIZE = 20;
    static final int HISTORY_LIMIT = 20;

    private OrderStatusPagination() {}

    static <T> Page<T> select(List<T> orders, Predicate<T> active, Comparator<T> newestFirst,
                              int requestedPage)
    {
        List<T> visible = new ArrayList<>(orders.stream().filter(active).sorted(newestFirst).toList());
        visible.addAll(orders.stream().filter(active.negate()).sorted(newestFirst)
                .limit(HISTORY_LIMIT).toList());
        int pageCount = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = page * PAGE_SIZE;
        return new Page<>(List.copyOf(visible.subList(from, Math.min(visible.size(), from + PAGE_SIZE))),
                page, pageCount);
    }

    record Page<T>(List<T> orders, int index, int count) {}
}
