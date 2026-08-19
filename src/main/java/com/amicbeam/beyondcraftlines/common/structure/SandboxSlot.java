package com.amicbeam.beyondcraftlines.common.structure;

public record SandboxSlot(int index, int originX, int originY, int originZ, int spacing)
{
    public SandboxSlot
    {
        if (index < 0 || spacing < 1) throw new IllegalArgumentException("invalid sandbox slot");
    }

    public int maxX(int width) { return originX + width - 1; }
    public int maxY(int height) { return originY + height - 1; }
    public int maxZ(int depth) { return originZ + depth - 1; }
}
