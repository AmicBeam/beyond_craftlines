package com.amicbeam.beyondcraftlines.common.dashboard;
public enum DashboardStockMode {
    NETWORK("network"), CONTAINER("container");
    private final String id; DashboardStockMode(String id){this.id=id;} public String id(){return id;}
    public DashboardStockMode next(){return this==NETWORK?CONTAINER:NETWORK;}
    public static DashboardStockMode byId(String id){return "container".equals(id)?CONTAINER:NETWORK;}
}
