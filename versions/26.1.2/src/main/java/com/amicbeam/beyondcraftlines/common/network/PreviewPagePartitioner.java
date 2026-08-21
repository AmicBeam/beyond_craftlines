package com.amicbeam.beyondcraftlines.common.network;

import java.util.ArrayList;
import java.util.List;

/** Splits independent preview streams without dropping any stream's tail. */
final class PreviewPagePartitioner
{
    private PreviewPagePartitioner() {}

    static <A, B> List<Page<A, B, Void>> partition(List<A> first, List<B> second, int pageSize)
    { return partition(first, second, List.<Void>of(), pageSize); }

    static <A, B, C> List<Page<A, B, C>> partition(List<A> first, List<B> second, List<C> third, int pageSize)
    {
        if (pageSize < 1) throw new IllegalArgumentException("page size must be positive");
        int pageCount = Math.max(1, Math.max(ceilDiv(first.size(), pageSize),
                Math.max(ceilDiv(second.size(), pageSize), ceilDiv(third.size(), pageSize))));
        List<Page<A, B, C>> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++)
        {
            int from = page * pageSize;
            pages.add(new Page<>(slice(first, from, pageSize), slice(second, from, pageSize),
                    slice(third, from, pageSize)));
        }
        return List.copyOf(pages);
    }

    private static int ceilDiv(int value, int divisor) { return (value + divisor - 1) / divisor; }

    private static <T> List<T> slice(List<T> values, int from, int pageSize)
    {
        if (from >= values.size()) return List.of();
        return List.copyOf(values.subList(from, Math.min(values.size(), from + pageSize)));
    }

    record Page<A, B, C>(List<A> first, List<B> second, List<C> third) {}
}
