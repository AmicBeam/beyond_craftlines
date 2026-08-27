package com.amicbeam.beyondcraftlines.common.runtime;

public enum OrderOutputDestination
{
    NETWORK("network"),
    INVENTORY("inventory");

    private final String id;

    OrderOutputDestination(String id) { this.id = id; }

    public String id() { return id; }
    public OrderOutputDestination next() { return this == NETWORK ? INVENTORY : NETWORK; }

    public static OrderOutputDestination byId(String id)
    {
        for (OrderOutputDestination value : values()) if (value.id.equals(id)) return value;
        return NETWORK;
    }
}
