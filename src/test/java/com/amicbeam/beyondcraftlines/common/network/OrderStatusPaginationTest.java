package com.amicbeam.beyondcraftlines.common.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderStatusPaginationTest
{
    private static final Comparator<Order> NEWEST_FIRST = Comparator.comparingInt(Order::created).reversed();

    @Test
    void oldUnfinishedOrdersStayAheadOfNewerTerminalOrders()
    {
        List<Order> orders = new ArrayList<>();
        orders.add(new Order(1, "QUEUED"));
        orders.add(new Order(2, "RUNNING"));
        orders.add(new Order(3, "PAUSED"));
        IntStream.range(4, 100).forEach(time -> orders.add(new Order(time, "COMPLETE")));

        var first = page(orders, 0);
        assertEquals(List.of(orders.get(2), orders.get(1), orders.get(0)), first.orders().subList(0, 3));
        List<Order> displayed = allPages(orders);
        assertEquals(23, displayed.size());
        assertEquals(20, displayed.stream().filter(order -> !order.active()).count());
        assertEquals(99, displayed.get(3).created());
        assertEquals(80, displayed.get(displayed.size() - 1).created());
    }

    @Test
    void moreThanTwentyActiveOrdersRemainReachableAcrossPages()
    {
        List<Order> orders = IntStream.range(0, 64).mapToObj(time -> new Order(time, "RUNNING")).toList();

        assertEquals(4, page(orders, 0).count());
        List<Order> displayed = allPages(orders);
        assertEquals(orders.stream().sorted(NEWEST_FIRST).toList(), displayed);
        assertTrue(displayed.stream().allMatch(Order::active));
        assertEquals(4, page(orders, 3).orders().size());

        List<Order> mixed = new ArrayList<>(orders);
        IntStream.range(64, 128).forEach(time -> mixed.add(new Order(time, "COMPLETE")));
        List<Order> withHistory = allPages(mixed);
        assertEquals(5, page(mixed, 0).count());
        assertEquals(displayed, withHistory.subList(0, 64));
        assertEquals(84, withHistory.size());
    }

    @Test
    void finishedAndFailedOrdersShareOnlyTheHistoryQuota()
    {
        List<Order> orders = IntStream.range(0, 60).mapToObj(time ->
                new Order(time, List.of("COMPLETE", "CANCELLED", "ERROR").get(time % 3))).toList();

        var first = page(orders, 0);
        assertEquals(1, first.count());
        assertEquals(orders.stream().sorted(NEWEST_FIRST).limit(20).toList(), first.orders());
    }

    @Test
    void pageClampsAfterOrdersLeaveAndForInvalidRequests()
    {
        List<Order> orders = IntStream.range(0, 21).mapToObj(time -> new Order(time, "RUNNING")).toList();
        assertEquals(1, page(orders, Integer.MAX_VALUE).index());
        assertEquals(0, page(orders, Integer.MIN_VALUE).index());
        var remaining = page(orders.subList(0, 1), 1);
        assertEquals(0, remaining.index());
        assertEquals(orders.subList(0, 1), remaining.orders());
        var empty = page(List.of(), 5);
        assertEquals(0, empty.index());
        assertEquals(1, empty.count());
        assertTrue(empty.orders().isEmpty());
    }

    private static OrderStatusPagination.Page<Order> page(List<Order> orders, int page)
    { return OrderStatusPagination.select(orders, Order::active, NEWEST_FIRST, page); }

    private static List<Order> allPages(List<Order> orders)
    {
        return IntStream.range(0, page(orders, 0).count()).mapToObj(index -> page(orders, index))
                .peek(page -> assertTrue(page.orders().size() <= 20))
                .flatMap(page -> page.orders().stream()).toList();
    }

    private record Order(int created, String status)
    {
        boolean active() { return List.of("QUEUED", "RUNNING", "PAUSED").contains(status); }
    }
}
