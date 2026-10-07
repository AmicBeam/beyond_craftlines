package com.amicbeam.beyondcraftlines.client;

import java.io.IOException;

/** Diagnostic counts only: actual catalog size never makes a cache ineligible. */
final class PlanningCatalogCacheCounts
{
    private long entries;
    private long stringBytes;

    void addRecipes(int count) throws IOException { add(count); }
    void addSlots(int count) throws IOException { add(count); }
    void addCandidates(int count) throws IOException { add(count); }
    void addByproducts(int count) throws IOException { add(count); }

    private void add(int count) throws IOException
    {
        requireNonNegative(count);
        entries += count;
    }

    void addStringBytes(int count) throws IOException
    {
        requireNonNegative(count);
        stringBytes += count;
    }

    private static void requireNonNegative(int count) throws IOException
    {
        if (count < 0) throw new IOException("negative cache length/count value=" + count);
    }

    long entries() { return entries; }
    long stringBytes() { return stringBytes; }
}
