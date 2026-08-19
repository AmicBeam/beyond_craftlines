package com.amicbeam.beyondcraftlines.common.structure;

import java.util.HashSet;
import java.util.Set;

public final class SandboxSlotAllocator
{
    private final int spacing;
    private final int columns;
    private final Set<Integer> allocated = new HashSet<>();

    public SandboxSlotAllocator(int spacing, int columns)
    {
        if (spacing < 1 || columns < 1) throw new IllegalArgumentException("invalid sandbox layout");
        this.spacing = spacing;
        this.columns = columns;
    }

    public SandboxSlot allocate()
    {
        int index = 0;
        while (allocated.contains(index)) index++;
        allocated.add(index);
        int column = index % columns;
        int row = index / columns;
        return new SandboxSlot(index, column * spacing, 64, row * spacing, spacing);
    }

    public boolean release(int index)
    {
        return allocated.remove(index);
    }

    public boolean isAllocated(int index)
    {
        return allocated.contains(index);
    }

    public int allocatedCount()
    {
        return allocated.size();
    }
}
