package com.amicbeam.beyondcraftlines.common.network;

import java.util.ArrayList;
import java.util.List;

/** Splits two independent preview streams without dropping either stream's tail. */
final class PreviewPagePartitioner
{
    private PreviewPagePartitioner() {}

    static <A, B> List<Page<A, B>> partition(List<A> first, List<B> second, int pageSize)
    {
        if (pageSize < 1) throw new IllegalArgumentException("page size must be positive");
        int pageCount = Math.max(1, Math.max(ceilDiv(first.size(), pageSize), ceilDiv(second.size(), pageSize)));
        List<Page<A, B>> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++)
        {
            int from = page * pageSize;
            pages.add(new Page<>(slice(first, from, pageSize), slice(second, from, pageSize)));
        }
        return List.copyOf(pages);
    }

    private static int ceilDiv(int value, int divisor) { return (value + divisor - 1) / divisor; }

    private static <T> List<T> slice(List<T> values, int from, int pageSize)
    {
        if (from >= values.size()) return List.of();
        return List.copyOf(values.subList(from, Math.min(values.size(), from + pageSize)));
    }

    record Page<A, B>(List<A> first, List<B> second) {}
}
