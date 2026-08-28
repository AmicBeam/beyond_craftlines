package com.amicbeam.beyondcraftlines.common.runtime;

public enum OrderOrigin
{
    MANUAL("manual"), AUTOMATIC("automatic");

    private final String id;
    OrderOrigin(String id) { this.id = id; }
    public String id() { return id; }
    public static OrderOrigin byId(String id)
    { return AUTOMATIC.id.equals(id) ? AUTOMATIC : MANUAL; }
}
