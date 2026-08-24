package com.amicbeam.beyondcraftlines.common.runtime;

public enum ProvisionerDeliveryStrategy
{
    ROUND_ROBIN("gui.beyond_craftlines.provisioner.delivery.round_robin"),
    NEAREST_FIRST("gui.beyond_craftlines.provisioner.delivery.nearest"),
    FARTHEST_FIRST("gui.beyond_craftlines.provisioner.delivery.farthest");

    private final String translationKey;

    ProvisionerDeliveryStrategy(String translationKey) { this.translationKey = translationKey; }

    public int id() { return ordinal(); }
    public String translationKey() { return translationKey; }
    public ProvisionerDeliveryStrategy next()
    { return values()[(ordinal() + 1) % values().length]; }
    public static boolean isValidId(int id) { return id >= 0 && id < values().length; }
    public static ProvisionerDeliveryStrategy fromId(int id)
    { return isValidId(id) ? values()[id] : ROUND_ROBIN; }
}
