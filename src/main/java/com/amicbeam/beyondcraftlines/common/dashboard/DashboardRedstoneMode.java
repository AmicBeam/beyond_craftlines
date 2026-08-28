package com.amicbeam.beyondcraftlines.common.dashboard;

public enum DashboardRedstoneMode
{
    IGNORE("ignore"), LOW("low"), HIGH("high"), PULSE("pulse");

    private final String id;
    DashboardRedstoneMode(String id) { this.id = id; }
    public String id() { return id; }
    public DashboardRedstoneMode next() { return values()[(ordinal() + 1) % values().length]; }
    public boolean allows(boolean powered, boolean risingEdge)
    {
        return switch (this)
        {
            case IGNORE -> true;
            case LOW -> !powered;
            case HIGH -> powered;
            case PULSE -> risingEdge;
        };
    }
    public static DashboardRedstoneMode byId(String id)
    {
        for (DashboardRedstoneMode value : values()) if (value.id.equals(id)) return value;
        return IGNORE;
    }
}
